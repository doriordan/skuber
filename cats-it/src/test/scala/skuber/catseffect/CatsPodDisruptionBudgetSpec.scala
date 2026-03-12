package skuber.catseffect

import cats.effect.IO
import munit.CatsEffectSuite
import scala.reflect.Selectable.reflectiveSelectable
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.json.format.*
import skuber.model.LabelSelector.dsl.*
import skuber.model.apps.v1.Deployment
import skuber.model.policy.v1.PodDisruptionBudget
import skuber.catseffect.TestHelpers.*

import scala.concurrent.duration.*

class CatsPodDisruptionBudgetSpec extends CatsEffectSuite:
  given LoggingContext = RequestLoggingContext()
  override def munitIOTimeout: Duration = 5.minutes

  val client = ResourceFunFixture(CatsKubernetesClient.resource[IO])

  client.test("create a PodDisruptionBudget") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxDeployment(name)).map(_.getOrElse(fail("Create deployment failed")))
      result <- k8s.create(
        PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
      ).map(_.getOrElse(fail("Create PDB failed")))
      _ = assertEquals(result.name, name)
      _ = assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(Left(1)), Some("app" is "nginx"))))
      _ <- k8s.delete[PodDisruptionBudget](name)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }

  client.test("update a PodDisruptionBudget") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxDeployment(name)).map(_.getOrElse(fail("Create deployment failed")))
      pdb <- k8s.create(
        PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
      ).map(_.getOrElse(fail("Create PDB failed")))
      existing <- retryUntil(
        k8s.get[PodDisruptionBudget](pdb.name).map(_.isRight),
        retries = 20, delay = 2.seconds, label = "PDB exists"
      ) >> k8s.get[PodDisruptionBudget](pdb.name).map(_.getOrElse(fail("Get PDB failed")))
      result <- k8s.update(existing).map(_.getOrElse(fail("Update PDB failed")))
      _ = assertEquals(result.name, name)
      _ <- k8s.delete[PodDisruptionBudget](name)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }

  client.test("delete a PodDisruptionBudget") { k8s =>
    val name = java.util.UUID.randomUUID().toString
    for
      _ <- k8s.create(getNginxDeployment(name)).map(_.getOrElse(fail("Create deployment failed")))
      pdb <- k8s.create(
        PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
      ).map(_.getOrElse(fail("Create PDB failed")))
      _ <- k8s.delete[PodDisruptionBudget](pdb.name)
      _ <- retryUntilGone(k8s.get[PodDisruptionBudget](pdb.name), retries = 40, delay = 3.seconds)
      _ <- k8s.delete[Deployment](name)
    yield ()
  }
