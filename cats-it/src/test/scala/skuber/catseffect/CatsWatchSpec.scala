package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{EventType, LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.LabelSelector.dsl.*
import skuber.model.apps.v1.{Deployment, DeploymentList}
import skuber.model.{Container, Pod}
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*
import scala.reflect.Selectable.reflectiveSelectable

class CatsWatchSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 15.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  def makeWatchDeployment(name: String): Deployment =
    val container = Container(name = "nginx", image = "nginx:1.29.2",
      command = List("sh"),
      args = List("-c", """echo "foo"; trap exit TERM; sleep infinity & wait"""))
    val template = Pod.Template.Spec.named("nginx").addContainer(container).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(template).withLabelSelector("app" is "nginx")

  client.test("watch deployment events from a resource version") { k8s =>
    val d1Name = java.util.UUID.randomUUID().toString
    val d2Name = java.util.UUID.randomUUID().toString
    for
      listResult <- k8s.list[DeploymentList]()
      currentRV = listResult.getOrElse(fail("List failed")).resourceVersion

      fiber <- k8s.getWatcher[Deployment].watchStartingFromVersion(currentRV)
        .collect { case Right(event) => event }
        .filter(e => e._object.name == d1Name || e._object.name == d2Name)
        .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
        .take(4)
        .compile.toList
        .start

      _ <- k8s.create(makeWatchDeployment(d1Name)).map(_.getOrElse(fail("Create d1 failed")))
      _ <- retryUntil(
        k8s.get[Deployment](d1Name).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "d1 available"
      )
      _ <- k8s.delete[Deployment](d1Name)

      // Pause to trigger multiple watch HTTP requests (reconnect behaviour test)
      _ <- IO.sleep(62.seconds)

      _ <- k8s.create(makeWatchDeployment(d2Name)).map(_.getOrElse(fail("Create d2 failed")))
      _ <- retryUntil(
        k8s.get[Deployment](d2Name).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "d2 available"
      )
      _ <- k8s.delete[Deployment](d2Name)
      _ <- IO.sleep(10.seconds)

      events <- fiber.joinWithNever
    yield
      val summary = events.map(e => (e._type, e._object.name))
      assertEquals(summary, List(
        (EventType.ADDED, d1Name), (EventType.DELETED, d1Name),
        (EventType.ADDED, d2Name), (EventType.DELETED, d2Name)
      ))
  }

  client.test("watch deployment events at cluster scope from a resource version") { k8s =>
    val d1Name = java.util.UUID.randomUUID().toString
    val d2Name = java.util.UUID.randomUUID().toString
    for
      listResult <- k8s.list[DeploymentList]()
      currentRV = listResult.getOrElse(fail("List failed")).resourceVersion

      fiber <- k8s.getWatcher[Deployment].watchClusterStartingFromVersion(currentRV)
        .collect { case Right(event) => event }
        .filter(e => e._object.name == d1Name || e._object.name == d2Name)
        .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
        .take(4)
        .compile.toList
        .start

      _ <- k8s.create(makeWatchDeployment(d1Name)).map(_.getOrElse(fail("Create d1 failed")))
      _ <- retryUntil(
        k8s.get[Deployment](d1Name).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "d1 available"
      )
      _ <- k8s.delete[Deployment](d1Name)
      _ <- IO.sleep(62.seconds)
      _ <- k8s.create(makeWatchDeployment(d2Name)).map(_.getOrElse(fail("Create d2 failed")))
      _ <- retryUntil(
        k8s.get[Deployment](d2Name).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "d2 available"
      )
      _ <- k8s.delete[Deployment](d2Name)
      _ <- IO.sleep(10.seconds)

      events <- fiber.joinWithNever
    yield
      val summary = events.map(e => (e._type, e._object.name))
      assertEquals(summary, List(
        (EventType.ADDED, d1Name), (EventType.DELETED, d1Name),
        (EventType.ADDED, d2Name), (EventType.DELETED, d2Name)
      ))
  }

  client.test("watch a specific named deployment") { k8s =>
    val dName = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(makeWatchDeployment(dName)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Deployment](dName).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "deployment available"
      )

      fiber <- k8s.getWatcher[Deployment].watchObject(dName)
        .collect { case Right(event) => event }
        .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
        .take(2)
        .compile.toList
        .start

      _ <- IO.sleep(62.seconds)
      _ <- k8s.delete[Deployment](dName)
      _ <- IO.sleep(10.seconds)

      events <- fiber.joinWithNever
    yield
      val summary = events.map(e => (e._type, e._object.name))
      assertEquals(summary, List(
        (EventType.ADDED, dName), (EventType.DELETED, dName)
      ))
  }

  client.test("watch a named deployment from a specific resource version sees only delete") { k8s =>
    val dName = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(makeWatchDeployment(dName)).map(_.getOrElse(fail("Create failed")))
      _ <- retryUntil(
        k8s.get[Deployment](dName).map(_.exists(_.status.exists(_.availableReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "deployment available"
      )

      d <- k8s.get[Deployment](dName).map(_.getOrElse(fail("Get failed")))

      fiber <- k8s.getWatcher[Deployment].watchObjectStartingFromVersion(dName, d.resourceVersion)
        .collect { case Right(event) => event }
        .filter(e => e._type == EventType.DELETED)
        .take(1)
        .compile.toList
        .start

      _ <- IO.sleep(62.seconds)
      _ <- k8s.delete[Deployment](dName)
      _ <- IO.sleep(10.seconds)

      events <- fiber.joinWithNever
    yield
      val summary = events.map(e => (e._type, e._object.name))
      assertEquals(summary, List((EventType.DELETED, dName)))
  }
