package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.api.client.{EventType, WatchParameters}
import scala.reflect.Selectable.reflectiveSelectable
import skuber.json.format.*
import skuber.model.LabelSelector.dsl.*
import skuber.model.{Container, Pod}
import skuber.model.apps.v1.{Deployment, DeploymentList}
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioWatchSpec:

  def makeWatchDeployment(name: String): Deployment =
    val container = Container(name = "nginx", image = "nginx:1.29.2",
      command = List("sh"),
      args = List("-c", """echo "foo"; trap exit TERM; sleep infinity & wait"""))
    val template = Pod.Template.Spec.named("nginx").addContainer(container).addLabel("app" -> "nginx")
    Deployment(name).withTemplate(template).withLabelSelector("app" is "nginx")

  def spec = suite("ZIO Watch")(

    test("watch deployment events from a resource version") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val d1Name = java.util.UUID.randomUUID().toString
        val d2Name = java.util.UUID.randomUUID().toString
        for
          list <- k8s.list[DeploymentList]()
          currentRV = list.resourceVersion

          fiber <- k8s.watch[Deployment](WatchParameters(resourceVersion = Some(currentRV)))
            .filter(e => e._object.name == d1Name || e._object.name == d2Name)
            .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
            .take(4)
            .runCollect
            .fork

          _ <- k8s.create(makeWatchDeployment(d1Name))
          _ <- retryUntil(
            k8s.get[Deployment](d1Name).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "d1 available"
          )
          _ <- k8s.delete[Deployment](d1Name)
          _ <- ZIO.sleep(62.seconds)
          _ <- k8s.create(makeWatchDeployment(d2Name))
          _ <- retryUntil(
            k8s.get[Deployment](d2Name).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "d2 available"
          )
          _ <- k8s.delete[Deployment](d2Name)
          _ <- ZIO.sleep(10.seconds)

          events <- fiber.join
        yield
          val summary = events.map(e => (e._type, e._object.name)).toList
          assertTrue(summary == List(
            (EventType.ADDED, d1Name), (EventType.DELETED, d1Name),
            (EventType.ADDED, d2Name), (EventType.DELETED, d2Name)
          ))
      }
    } @@ TestAspect.timeout(5.minutes),

    test("watch deployment events at cluster scope from a resource version") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val d1Name = java.util.UUID.randomUUID().toString
        val d2Name = java.util.UUID.randomUUID().toString
        for
          list <- k8s.list[DeploymentList]()
          currentRV = list.resourceVersion

          fiber <- k8s.watch[Deployment](WatchParameters(clusterScope = true, resourceVersion = Some(currentRV)))
            .filter(e => e._object.name == d1Name || e._object.name == d2Name)
            .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
            .take(4)
            .runCollect
            .fork

          _ <- k8s.create(makeWatchDeployment(d1Name))
          _ <- retryUntil(
            k8s.get[Deployment](d1Name).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "d1 available"
          )
          _ <- k8s.delete[Deployment](d1Name)
          _ <- ZIO.sleep(62.seconds)
          _ <- k8s.create(makeWatchDeployment(d2Name))
          _ <- retryUntil(
            k8s.get[Deployment](d2Name).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "d2 available"
          )
          _ <- k8s.delete[Deployment](d2Name)
          _ <- ZIO.sleep(10.seconds)

          events <- fiber.join
        yield
          val summary = events.map(e => (e._type, e._object.name)).toList
          assertTrue(summary == List(
            (EventType.ADDED, d1Name), (EventType.DELETED, d1Name),
            (EventType.ADDED, d2Name), (EventType.DELETED, d2Name)
          ))
      }
    } @@ TestAspect.timeout(5.minutes),

    test("watch a specific named deployment") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val dName = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(makeWatchDeployment(dName))
          _ <- retryUntil(
            k8s.get[Deployment](dName).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "deployment available"
          )

          fiber <- k8s.watch[Deployment](WatchParameters(fieldSelector = Some(s"metadata.name=$dName")))
            .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
            .take(2)
            .runCollect
            .fork

          _ <- ZIO.sleep(62.seconds)
          _ <- k8s.delete[Deployment](dName)
          _ <- ZIO.sleep(10.seconds)

          events <- fiber.join
        yield
          val summary = events.map(e => (e._type, e._object.name)).toList
          assertTrue(summary == List((EventType.ADDED, dName), (EventType.DELETED, dName)))
      }
    } @@ TestAspect.timeout(5.minutes),

    test("watch a named deployment from a specific resource version sees only delete") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val dName = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(makeWatchDeployment(dName))
          _ <- retryUntil(
            k8s.get[Deployment](dName).map(_.status.exists(_.availableReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "deployment available"
          )
          d <- k8s.get[Deployment](dName)

          fiber <- k8s.watch[Deployment](WatchParameters(
            fieldSelector = Some(s"metadata.name=$dName"),
            resourceVersion = Some(d.resourceVersion)
          ))
            .filter(e => e._type == EventType.DELETED)
            .take(1)
            .runCollect
            .fork

          _ <- ZIO.sleep(62.seconds)
          _ <- k8s.delete[Deployment](dName)
          _ <- ZIO.sleep(10.seconds)

          events <- fiber.join
        yield
          val summary = events.map(e => (e._type, e._object.name)).toList
          assertTrue(summary == List((EventType.DELETED, dName)))
      }
    } @@ TestAspect.timeout(5.minutes)

  ) @@ TestAspect.timeout(10.minutes)
