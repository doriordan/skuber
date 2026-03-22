package skuber.catseffect

import munit.FunSuite
import skuber.model.*
import skuber.model.apps.v1.Deployment
import skuber.internal.UrlBuilder

class UrlBuilderSpec extends FunSuite:

  val clusterServer = "https://k8s.example.com"
  val namespace = "default"

  test("namespaced core resource with name (Pod)"):
    val rd = summon[ResourceDefinition[Pod]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, nameComponent = Some("my-pod"))
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/pods/my-pod")

  test("namespaced core resource list (Pod, no name)"):
    val rd = summon[ResourceDefinition[Pod]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/pods")

  test("cluster-scoped resource (Namespace)"):
    val rd = summon[ResourceDefinition[Namespace]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, nameComponent = Some("kube-system"))
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/kube-system")

  test("namespace override"):
    val rd = summon[ResourceDefinition[Pod]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, nameComponent = Some("my-pod"), namespaceOverride = Some("kube-system"))
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/kube-system/pods/my-pod")

  test("status subresource URL"):
    val rd = summon[ResourceDefinition[Pod]]
    val url = UrlBuilder.statusUrl(clusterServer, namespace, rd, "my-pod")
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/pods/my-pod/status")

  test("scale subresource URL for Deployment"):
    val rd = summon[ResourceDefinition[Deployment]]
    val url = UrlBuilder.scaleUrl(clusterServer, namespace, rd, "my-deploy")
    assertEquals(url, "https://k8s.example.com/apis/apps/v1/namespaces/default/deployments/my-deploy/scale")

  test("pod log URL"):
    val url = UrlBuilder.podLogUrl(clusterServer, namespace, "my-pod")
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/pods/my-pod/log")

  test("exec URL"):
    val url = UrlBuilder.execUrl(clusterServer, namespace, "my-pod")
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/pods/my-pod/exec")

  test("cluster-scoped resource list (Namespace, no name)"):
    val rd = summon[ResourceDefinition[Namespace]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd)
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces")

  test("clusterScopeOverride=true skips namespace"):
    val rd = summon[ResourceDefinition[Pod]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, nameComponent = Some("my-pod"), clusterScopeOverride = Some(true))
    assertEquals(url, "https://k8s.example.com/api/v1/pods/my-pod")

  test("clusterScopeOverride=false includes namespace for cluster-scoped resource"):
    val rd = summon[ResourceDefinition[Namespace]]
    val url = UrlBuilder.resourceUrl(clusterServer, namespace, rd, nameComponent = Some("kube-system"), clusterScopeOverride = Some(false))
    assertEquals(url, "https://k8s.example.com/api/v1/namespaces/default/namespaces/kube-system")
