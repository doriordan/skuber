package skuber.zioit

import zio.*
import zio.test.*
import zio.test.test
import skuber.api.client.{DeleteOptions, DeletePropagation}
import skuber.json.format.*
import skuber.model.apps.v1.Deployment
import skuber.zio.ZKubernetesClient
import skuber.zioit.TestHelpers.*

object ZioDeploymentSpec:

  def spec = suite("ZIO Deployment")(

    test("deployment CRUD lifecycle") {
      ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
        val name = java.util.UUID.randomUUID().toString
        for
          created <- k8s.create(getNginxDeployment(name))
          _ = assert(created.name == name)
          got <- k8s.get[Deployment](name)
          _ = assert(got.name == name)
          _ <- retryConflict(
            k8s.get[Deployment](name).flatMap { d =>
              k8s.update(d.updateContainer(getNginxContainer("1.9.1")))
            }
          )
          _ <- retryUntil(
            k8s.get[Deployment](name).map(_.status.exists(_.updatedReplicas == 1)),
            retries = 40, delay = 5.seconds, label = "updatedReplicas == 1"
          )
          _ <- k8s.deleteWithOptions[Deployment](name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
          _ <- retryUntilGone(k8s.get[Deployment](name), retries = 40, delay = 5.seconds)
        yield assertTrue(true)
      }
    }

  )
