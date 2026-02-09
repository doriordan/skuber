package skuber.operator.event

import org.apache.pekko.actor.ActorSystem
import skuber.api.client.RequestLoggingContext
import skuber.json.format.eventFmt
import skuber.model.{Event, ObjectMeta, ObjectReference, ObjectResource}
import skuber.pekkoclient.PekkoKubernetesClient
import skuber.operator.reconciler.OperatorLogger

import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

/**
 * Event types for Kubernetes events.
 */
object EventType:
  val Normal = "Normal"
  val Warning = "Warning"

/**
 * Records Kubernetes events for a resource.
 *
 * Events are visible via `kubectl describe` and `kubectl get events`.
 * Use them to communicate significant occurrences during reconciliation.
 *
 * Usage:
 * {{{
 * ctx.eventRecorder.event(EventType.Normal, "ScaledUp", "Scaled deployment to 3 replicas")
 * ctx.eventRecorder.warning("FailedCreate", "Failed to create pod: quota exceeded")
 * }}}
 */
class EventRecorder(
  client: PekkoKubernetesClient,
  resource: ObjectResource,
  component: String = "skuber-operator"
)(using system: ActorSystem):

  given ExecutionContext = system.dispatcher
  given lc: RequestLoggingContext = RequestLoggingContext()

  private val log = OperatorLogger("skuber.operator.event.EventRecorder")

  /**
   * Record a normal event.
   */
  def normal(reason: String, message: String): Future[Unit] =
    event(EventType.Normal, reason, message)

  /**
   * Record a warning event.
   */
  def warning(reason: String, message: String): Future[Unit] =
    event(EventType.Warning, reason, message)

  /**
   * Record an event with the specified type.
   *
   * @param eventType "Normal" or "Warning"
   * @param reason Short, machine-readable reason for the event
   * @param message Human-readable description
   */
  def event(eventType: String, reason: String, message: String): Future[Unit] =
    val now = ZonedDateTime.now()

    val involvedObject = ObjectReference(
      kind = resource.kind,
      apiVersion = resource.apiVersion,
      namespace = resource.metadata.namespace,
      name = resource.metadata.name,
      uid = resource.metadata.uid,
      resourceVersion = resource.metadata.resourceVersion
    )

    val eventName = s"${resource.metadata.name}.${now.toInstant.toEpochMilli.toHexString}"

    val k8sEvent = Event(
      metadata = ObjectMeta(
        name = eventName,
        namespace = resource.metadata.namespace
      ),
      involvedObject = involvedObject,
      reason = Some(reason),
      message = Some(message),
      source = Some(Event.Source(component = Some(component))),
      firstTimestamp = Some(now),
      lastTimestamp = Some(now),
      count = Some(1),
      `type` = Some(eventType)
    )

    val k8s = client.usingNamespace(resource.metadata.namespace)

    k8s.create(k8sEvent)
      .map(_ => ())
      .recover {
        case e =>
          log.warn(s"Failed to record event: ${e.getMessage}")
      }
