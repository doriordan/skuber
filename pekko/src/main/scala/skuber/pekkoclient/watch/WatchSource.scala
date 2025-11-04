package skuber.pekkoclient.watch

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.SourceShape
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Source}
import play.api.libs.json.Format
import skuber.pekkoclient.Pool
import skuber.pekkoclient.impl.PekkoKubernetesClientImpl
import skuber.api.client._
import skuber.model.{ObjectResource, ResourceDefinition}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

private[pekkoclient] object WatchSource {

  import skuber.api.client.WatchStream._

  def apply[O <: ObjectResource](
    client: PekkoKubernetesClientImpl,
    pool: Pool[Start[O]],
    options: ListOptions,
    bufSize: Int,
    errorHandler: Option[String => _],
    overrideNamespace: Option[String] = None,
    overrideClusterScope: Option[Boolean] = None)
  (implicit format: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Source[WatchEvent[O], NotUsed] = {
    Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      implicit val sys = client.actorSystem
      implicit val dispatcher: ExecutionContext = sys.dispatcher

      val watchLoggerId = Random.nextInt().abs

      def createWatchRequest(since: Option[String]): Future[HttpRequest] = {
        val watchOptions = options.copy(
          resourceVersion = since,
          watch = Some(true)
        )
        client.buildRequest(
          HttpMethods.GET, rd, None, query = Some(Uri.Query(watchOptions.asMap)), overrideNamespace, overrideClusterScope
        )
      }

      val singleEnd = Source.single(End[O]())

      def singleStart(s:StreamElement[O]) = Source.single(s)

      val initSource = Source.futureSource(createWatchRequest(options.resourceVersion)
          .map(r => Source.single(r, Start[O](options.resourceVersion))))

      val httpFlow: Flow[(HttpRequest, Start[O]), StreamElement[O], NotUsed] =
        Flow[(HttpRequest, Start[O])].map { request => // log request
          client.logInfo(client.logConfig.logRequestBasic, s"Sending watch request (watchId=$watchLoggerId,method=${request._1.method.value},uri=${request._1.uri.toString})")
          request
        }.via(pool).flatMapConcat {
          case (Success(HttpResponse(StatusCodes.OK, _, entity, _)), se) =>
            client.logInfo(client.logConfig.logResponseBasic, s"Received response with HTTP status 200 (watchId=$watchLoggerId)")
            singleStart(se).concat(
              BytesToWatchEventSource[O](client, entity.dataBytes, bufSize, errorHandler).map { event =>
                Result[O](event._object.resourceVersion, event)
              }
            ).concat(singleEnd)
          case (Success(HttpResponse(sc, _, entity, _)), _) =>
            client.logWarn(s"Error watching resource (watchId=$watchLoggerId, http status =${sc.intValue()})")
            entity.discardBytes()
            throw new K8SException(Status(message = Some("Non-OK status code received while watching resource"), code = Some(sc.intValue())))
          case (Failure(f), _) =>
            client.logError(s"Error watching resource (watchId=$watchLoggerId)", f)
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
        }.filter(_.state == Finished).mapAsync(1) { acc =>
          createWatchRequest(acc.currentResourceVersion).map(r => (r, Start[O](acc.currentResourceVersion)))
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
