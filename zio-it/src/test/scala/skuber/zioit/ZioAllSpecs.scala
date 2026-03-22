package skuber.zioit

import zio.test.*
import skuber.zio.ZKubernetesClient

/** Single entry point for all ZIO integration tests.
 *
 *  All suites share one ZKubernetesClient layer so the underlying Netty event
 *  loop is created once and torn down only when the entire test run finishes,
 *  avoiding "event executor terminated" errors that occur when each suite
 *  independently creates and destroys the HTTP client.
 */
object ZioAllSpecs extends ZIOSpecDefault:

  def spec = suite("ZIO Kubernetes Integration")(
    ZioKubernetesClientIT.spec,
    ZioPodSpec.spec,
    ZioDeploymentSpec.spec,
    ZioServiceSpec.spec,
    ZioNamespaceSpec.spec,
    ZioPatchSpec.spec,
    ZioHpaSpec.spec,
    ZioPodDisruptionBudgetSpec.spec,
    ZioPodLogSpec.spec,
    ZioExecSpec.spec,
    ZioCustomResourceSpec.spec,
    ZioWatchSpec.spec
  ).provideLayerShared(ZKubernetesClient.layer) @@ TestAspect.sequential @@ TestAspect.withLiveClock
