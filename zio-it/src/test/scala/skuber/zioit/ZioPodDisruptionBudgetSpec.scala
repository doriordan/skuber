package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import scala.reflect.Selectable.reflectiveSelectable
import skuber.json.format.*
import skuber.model.LabelSelector.dsl.*
import skuber.model.apps.v1.Deployment
import skuber.model.policy.v1.PodDisruptionBudget
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioPodDisruptionBudgetSpec:

  def spec = suite("ZIO PodDisruptionBudget")(

    test("create a PodDisruptionBudget") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxDeployment(name))
          result <- k8s.create(
            PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
          )
          _ = assert(result.name == name)
          _ = assert(result.spec.contains(PodDisruptionBudget.Spec(None, Some(Left(1)), Some("app" is "nginx"))))
          _ <- k8s.delete[PodDisruptionBudget](name)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    },

    test("update a PodDisruptionBudget") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxDeployment(name))
          pdb <- k8s.create(
            PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
          )
          _ <- retryUntil(
            k8s.getOption[PodDisruptionBudget](pdb.name).map(_.isDefined),
            retries = 20, delay = 2.seconds, label = "PDB exists"
          )
          result <- retryConflict(k8s.get[PodDisruptionBudget](pdb.name).flatMap(k8s.update))
          _ = assert(result.name == name)
          _ <- k8s.delete[PodDisruptionBudget](name)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    },

    test("delete a PodDisruptionBudget") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          _ <- k8s.create(getNginxDeployment(name))
          pdb <- k8s.create(
            PodDisruptionBudget(name).withMinAvailable(Left(1)).withLabelSelector("app" is "nginx")
          )
          _ <- k8s.delete[PodDisruptionBudget](pdb.name)
          _ <- retryUntilGone(k8s.get[PodDisruptionBudget](pdb.name), retries = 40, delay = 3.seconds)
          _ <- k8s.delete[Deployment](name)
        yield assertTrue(true)
      }
    }

  )
