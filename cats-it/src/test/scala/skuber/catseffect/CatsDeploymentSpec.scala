package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import skuber.api.client.{DeleteOptions, DeletePropagation, LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.apps.v1.Deployment
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsDeploymentSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 10.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("deployment CRUD lifecycle") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      created <- k8s.create(getNginxDeployment(name))
        .map(_.getOrElse(fail("Create failed")))
      _ = assertEquals(created.name, name)
      got <- k8s.get[Deployment](name)
        .map(_.getOrElse(fail("Get failed")))
      _ = assertEquals(got.name, name)
      updated <- retryConflict(
        k8s.get[Deployment](name).flatMap {
          case Right(d) => k8s.update(d.updateContainer(getNginxContainer("1.9.1")))
          case Left(s)  => IO.pure(Left(s))
        }
      ).map(_.getOrElse(fail("Update failed")))
      _ <- retryUntil(
        k8s.get[Deployment](name).map(_.exists(_.status.exists(_.updatedReplicas == 1))),
        retries = 40, delay = 5.seconds, label = "updatedReplicas == 1"
      )
      _ <- k8s.deleteWithOptions[Deployment](name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
      _ <- retryUntilGone(k8s.get[Deployment](name), retries = 40, delay = 5.seconds)
    yield ()
  }
