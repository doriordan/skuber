package skuber.catseffect

import cats.effect.IO
import scala.reflect.Selectable.reflectiveSelectable
import skuber.model.LabelSelector.dsl.*
import skuber.model.apps.v1.Deployment
import skuber.model.{Container, ObjectMeta, Pod}

import scala.concurrent.duration.*

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

  /** Retries `io` until it returns `true`, or fails after `retries` attempts. */
  def retryUntil(
    io: IO[Boolean],
    retries: Int = 40,
    delay: FiniteDuration = 3.seconds,
    label: String = "condition"
  ): IO[Unit] =
    io.flatMap { result =>
      if result then IO.unit
      else if retries <= 0 then IO.raiseError(new AssertionError(s"Timed out waiting for: $label"))
      else IO.sleep(delay) >> retryUntil(io, retries - 1, delay, label)
    }

  /** Retries `thunk` on 409 Conflict by re-executing the entire thunk (re-fetch + re-apply). */
  def retryConflict[T](
    thunk: => IO[Either[skuber.api.client.K8SException, T]],
    retries: Int = 5,
    delay: FiniteDuration = 500.milliseconds
  ): IO[Either[skuber.api.client.K8SException, T]] =
    thunk.flatMap {
      case left @ Left(ex) if ex.isConflict && retries > 0 =>
        IO.sleep(delay) >> retryConflict(thunk, retries - 1, delay)
      case other => IO.pure(other)
    }

  /** Retries `io` until it returns `Left` with a 404, confirming resource deletion. */
  def retryUntilGone[T](
    io: IO[Either[skuber.api.client.K8SException, T]],
    retries: Int = 40,
    delay: FiniteDuration = 3.seconds
  ): IO[Unit] =
    io.flatMap {
      case Left(ex) if ex.isNotFound => IO.unit
      case Left(other) => IO.raiseError(new AssertionError(s"Unexpected error waiting for deletion: $other"))
      case Right(_) =>
        if retries <= 0 then IO.raiseError(new AssertionError("Timed out waiting for resource to be deleted"))
        else IO.sleep(delay) >> retryUntilGone(io, retries - 1, delay)
    }
