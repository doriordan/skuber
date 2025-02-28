package skuber.akkaclient.watch

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.stream.SourceShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Source}
import play.api.libs.json.Format
import skuber.akkaclient.Pool
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client._
import skuber.model.{ObjectResource, ResourceDefinition}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

private[akkaclient] object WatchSource {

  import skuber.api.client.WatchStream._

  def apply[O <: ObjectResource](
    client: AkkaKubernetesClientImpl,
    pool: Pool[Start[O]],
    options: ListOptions,
    bufSize: Int,
    errorHandler: Option[String => _],
    overrideNamespace: Option[String] = None,
    overrideClusterScope: Option[Boolean] = None)(implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], NotUsed] =
  {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      implicit val sys = client.actorSystem
      implicit val dispatcher: ExecutionContext = sys.dispatcher

      def createWatchRequest(since: Option[String]) =
      {
        val watchOptions=options.copy(
          resourceVersion = since,
          watch = Some(true)
        )
        val req = client.buildRequest(
          HttpMethods.GET, rd, None, query =  Some(Uri.Query(watchOptions.asMap)),  overrideNamespace, overrideClusterScope
        )
        req
      }

      val singleEnd = Source.single(End[O]())

      def singleStart(s:StreamElement[O]) = Source.single(s)

      val initSource = Source.single(
        (createWatchRequest(options.resourceVersion), Start[O](options.resourceVersion))
      )

      val httpFlow: Flow[(HttpRequest, Start[O]), StreamElement[O], NotUsed] =
        Flow[(HttpRequest, Start[O])].map { request => // log request
          client.logInfo(client.logConfig.logRequestBasic, s"about to send HTTP request: ${request._1.method.value} ${request._1.uri.toString}")
          request
        }.via(pool).flatMapConcat {
          case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), se) =>
            client.logInfo(client.logConfig.logResponseBasic, s"received response with HTTP status 200")
            singleStart(se).concat(
              BytesToWatchEventSource[O](client, entity.dataBytes, bufSize, errorHandler).map { event =>
                Result[O](event._object.resourceVersion, event)
              }
            ).concat(singleEnd)
          case (Success(HttpResponse(sc, _, entity, _)), _) =>
            client.logWarn(s"Error watching resource. Received a status of ${sc.intValue()}")
            entity.discardBytes()
            throw new K8SException(Status(message = Some("Non-OK status code received while watching resource"), code = Some(sc.intValue())))
          case (Failure(f), _) =>
            client.logError("Error watching resource.", f)
            throw new K8SException(Status(message = Some("Error watching resource"), details = Some(f.getMessage)))
        }

      val outboundFlow: Flow[StreamElement[O], WatchEvent[O], NotUsed] =
        Flow[StreamElement[O]]
          .collect {
            case Result(_, event) => event
          }

      val feedbackFlow: Flow[StreamElement[O], (HttpRequest, Start[O]), NotUsed] =
        Flow[StreamElement[O]].scan(StreamContext(None, Waiting)){(cxt, next) =>
          next match {
            case Start(rv) => StreamContext(rv, Processing)
            case Result(rv, _) => StreamContext(Some(rv), Processing)
            case End() => cxt.copy(state = Finished)
          }
        }.filter(_.state == Finished).map { acc =>
          (createWatchRequest(acc.currentResourceVersion), Start[O](acc.currentResourceVersion))
        }

      val init = b.add(initSource)
      val http = b.add(httpFlow)
      val merge = b.add(Merge[(HttpRequest, Start[O])](2))
      val broadcast = b.add(Broadcast[StreamElement[O]](2, eagerCancel = true))
      val outbound = b.add(outboundFlow)
      val feedback = b.add(feedbackFlow)

      init ~> merge ~> http     ~> broadcast ~> outbound
              merge <~ feedback <~ broadcast

      SourceShape(outbound.out)
    })
  }
}
