package skuber.api.client.exec

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpHeader, StatusCodes, Uri, ws}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.stream.SinkShape
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Partition, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import play.api.libs.json.JsString
import skuber.api.client.impl.KubernetesClientImpl
import skuber.api.client.{K8SException, LoggingContext, Status}
import skuber.api.security.HTTPRequestAuth
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Implementation of pod exec support
 */
object PodExecImpl {

  private[client] def exec(requestContext: KubernetesClientImpl,
                            podName: String,
                            command: Seq[String],
                            maybeContainerName: Option[String] = None,
                            maybeStdin: Option[Source[String, _]] = None,
                            maybeStdout: Option[Sink[String, _]] = None,
                            maybeStderr: Option[Sink[String, _]] = None,
                            tty: Boolean = false,
                            maybeClose: Option[Promise[Unit]] = None,
                            namespace: Option[String] = None)(implicit sys: ActorSystem, lc: LoggingContext): Future[Unit] = {
    implicit val executor: ExecutionContext = sys.dispatcher

    val containerPrintName = maybeContainerName.getOrElse("<none>")
    requestContext.log.info(s"Trying to connect to container ${containerPrintName} of pod ${podName}")

    // Compose queries
    var queries: Seq[(String, String)] = Seq("stdin" -> maybeStdin.isDefined.toString,
      "stdout" -> maybeStdout.isDefined.toString,
      "stderr" -> maybeStderr.isDefined.toString,
      "tty" -> tty.toString)
    maybeContainerName.foreach { containerName =>
      queries ++= Seq("container" -> containerName)
    }
    queries ++= command.map("command" -> _)

    // Determine scheme and connection context based on SSL context
    val (scheme, connectionContext) = requestContext.sslContext match {
      case Some(ssl) =>
        ("wss", ConnectionContext.httpsClient { (host, port) =>
          val engine = ssl.createSSLEngine(host, port)
          engine.setEnabledProtocols(Array("TLSv1.2", "TLSv1"))
          engine.setUseClientMode(true)
          engine
        })
      case None =>
        ("ws", Http().defaultClientHttpsContext)
    }

    // Compose URI
    val namespaceName = namespace.getOrElse(requestContext.namespaceName)

    val uri = Uri(requestContext.clusterServer)
      .withScheme(scheme)
      .withPath(Uri.Path(s"/api/v1/namespaces/$namespaceName/pods/$podName/exec"))
      .withQuery(Uri.Query(queries: _*))

    // Compose headers
    var headers: List[HttpHeader] = List(RawHeader("Accept", "*/*"))
    headers ++= HTTPRequestAuth.getAuthHeader(requestContext.requestAuth).map(a => List(a)).getOrElse(List())

    // Convert `String` to `ByteString`, then prepend channel bytes
    val source: Source[ws.Message, Promise[Option[ws.Message]]] = maybeStdin.getOrElse(Source.empty).viaMat(Flow[String].map { s =>
      ws.BinaryMessage(ByteString(0).concat(ByteString(s)))
    })(Keep.right).concatMat(Source.maybe[ws.Message])(Keep.right)

    // Split the sink from websocket into stdout and stderr then remove first bytes which indicate channels
    val sink: Sink[ws.Message, NotUsed] = Sink.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val partition = builder.add(Partition[ws.Message](2, {
        case bm: ws.BinaryMessage.Strict if bm.data(0) == 1 =>
          0
        case bm: ws.BinaryMessage.Strict if bm.data(0) == 2 =>
          1
      }))

      def convertSink = Flow[ws.Message].map[String] {
        case bm: ws.BinaryMessage.Strict =>
          bm.data.utf8String.substring(1)
      }

      partition.out(0) ~> convertSink ~> maybeStdout.getOrElse(Sink.ignore)
      partition.out(1) ~> convertSink ~> maybeStderr.getOrElse(Sink.ignore)

      SinkShape(partition.in)
    })


    // Make a flow from the source to the sink
    val flow: Flow[ws.Message, ws.Message, Promise[Option[ws.Message]]] = Flow.fromSinkAndSourceMat(sink, source)(Keep.right)

    // upgradeResponse completes or fails when the connection succeeds or fails
    // and promise controls the connection close timing
    val (upgradeResponse, promise) = Http().singleWebSocketRequest(ws.WebSocketRequest(uri, headers, subprotocol = Option("channel.k8s.io")), flow, connectionContext)

    val connected: Future[Done] = upgradeResponse.flatMap { upgrade =>
      // just like a regular http request we can access response status which is available via upgrade.response.status
      // status code 101 (Switching Protocols) indicates that server support WebSockets
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        val detailsF = Unmarshal(upgrade.response.entity).to[String]
        detailsF.map { details =>
          throw new K8SException(Status(message =
            Some(s"Connection failed with status ${upgrade.response.status}"), code = Some(upgrade.response.status.intValue()), details = Some(JsString(details))))
            Done
        }
      }
    }


    val close = maybeClose.getOrElse(Promise.successful(()))
    connected.foreach { _ =>
      requestContext.log.info(s"Connected to container ${containerPrintName} of pod ${podName}")
      close.future.foreach { _ =>
        requestContext.log.info(s"Close the connection of container ${containerPrintName} of pod ${podName}")
        promise.trySuccess(None)
      }
    }
    Future.sequence(Seq(connected, close.future, promise.future)).map { _ => () }
  }
}
