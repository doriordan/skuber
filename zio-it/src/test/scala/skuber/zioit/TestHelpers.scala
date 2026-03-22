package skuber.zioit

import zio.*
import zio.stream.*
import scala.reflect.Selectable.reflectiveSelectable
import skuber.model.LabelSelector.dsl.*
import skuber.model.apps.v1.Deployment
import skuber.model.{Container, ObjectMeta, Pod}
import skuber.zio.K8sException

object TestHelpers:
  val defaultNginxVersion = "1.29.2"

  def getNginxContainer(version: String = defaultNginxVersion, containerName: String = "nginx"): Container =
    Container(name = containerName, image = s"nginx:$version").exposePort(80)

  def getNginxPod(name: String, version: String = defaultNginxVersion): Pod =
    val container = getNginxContainer(version)
    Pod(
      metadata = ObjectMeta(name = name, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1")),
      spec = Some(Pod.Spec(containers = List(container)))
    )

  def getNginxPodWithLabels(name: String, labels: Map[String, String], version: String = defaultNginxVersion): Pod =
    val container = getNginxContainer(version)
    Pod(metadata = ObjectMeta(name = name, labels = labels), spec = Some(Pod.Spec(containers = List(container))))

  def getNginxPodWithNamespace(namespace: String, name: String, version: String = defaultNginxVersion): Pod =
    val container = getNginxContainer(version)
    Pod(
      metadata = ObjectMeta(name = name, namespace = namespace, labels = Map("label" -> "1"), annotations = Map("annotation" -> "1")),
      spec = Some(Pod.Spec(containers = List(container)))
    )

  def getNginxDeployment(name: String, version: String = defaultNginxVersion): Deployment =
    val container = getNginxContainer(version)
    val template = Pod.Template.Spec.named("nginx").addContainer(container).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(template).withLabelSelector("app" is "nginx")

  /** Retries `io` until it returns `true`, or fails after `retries` attempts. Errors are treated as `false`. */
  def retryUntil(
    io: ZIO[Any, Any, Boolean],
    retries: Int = 40,
    delay: Duration = 3.seconds,
    label: String = "condition"
  ): Task[Unit] =
    io.orElseSucceed(false).flatMap { result =>
      if result then ZIO.unit
      else if retries <= 0 then ZIO.fail(new AssertionError(s"Timed out waiting for: $label"))
      else ZIO.sleep(delay) *> retryUntil(io, retries - 1, delay, label)
    }

  /** Retries `thunk` on 409 Conflict by re-executing the entire thunk. */
  def retryConflict[T](
    thunk: => IO[K8sException, T],
    retries: Int = 5,
    delay: Duration = 500.milliseconds
  ): IO[K8sException, T] =
    thunk.catchSome {
      case e if e.isConflict && retries > 0 =>
        ZIO.sleep(delay) *> retryConflict(thunk, retries - 1, delay)
    }

  /** Retries `io` until it fails with 404, confirming resource deletion. */
  def retryUntilGone[T](
    io: IO[K8sException, T],
    retries: Int = 40,
    delay: Duration = 3.seconds
  ): Task[Unit] =
    io.foldZIO(
      e =>
        if e.isNotFound then ZIO.succeed(true)
        else ZIO.fail(new AssertionError(s"Unexpected error waiting for deletion: ${e.getMessage}")),
      _ => ZIO.succeed(false)
    ).flatMap { gone =>
      if gone then ZIO.unit
      else if retries <= 0 then ZIO.fail(new AssertionError("Timed out waiting for resource to be deleted"))
      else ZIO.sleep(delay) *> retryUntilGone(io, retries - 1, delay)
    }
