package skuber.format

import java.util.UUID.randomUUID
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import scala.concurrent.duration._
import skuber.Container
import skuber.DNSPolicy
import skuber.FutureUtil.FutureOps
import skuber.K8SFixture
import skuber.LabelSelector
import skuber.Pod
import skuber.PodList
import skuber.Resource.Quantity
import skuber.RestartPolicy
import skuber.Security.RuntimeDefaultProfile
import skuber.json.format._
import skuber.k8sInit

class PodFormatSpec extends K8SFixture with Eventually with Matchers with BeforeAndAfterAll with ScalaFutures {
  val defaultLabels = Map("PodFormatSpec" -> this.suiteName)
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

  val namePrefix: String = "foo-"
  val podName:    String = namePrefix + randomUUID().toString
  val containerName = "nginx"
  val nginxVersion  = "1.7.9"
  val nginxImage    = s"nginx:$nginxVersion"

  val podJsonStr = s"""
    {
      "kind": "Pod",
      "apiVersion": "v1",
      "metadata": {
        "name": "$podName",
        "generateName": "$namePrefix",
        "namespace": "default",
        "selfLink": "/api/v1/namespaces/default/pods/$podName",
        "labels": {
          ${defaultLabels.toList.map(v => s""""${v._1}": "${v._2}"""").mkString(",")}
        }
      },
      "spec": {
        "securityContext": {
          "fsGroup": 1001,
          "runAsGroup": 1001,
          "runAsNonRoot": true,
          "runAsUser": 1001,
          "seccompProfile": {
            "type": "RuntimeDefault"
          }
        },
        "volumes": [
          {
            "name": "test-empty-dir-volume",
            "emptyDir": {
              "sizeLimit": "100Mi"
            }
          }
        ],
        "containers": [
          {
            "name": "$containerName",
            "image": "$nginxImage",
            "resources": {
              "limits": {
                "cpu": "250m"
              }
            },
            "volumeMounts": [
              {
                "name": "test-empty-dir-volume",
                "readOnly": true,
                "mountPath": "/test-dir"
              }
            ],
            "livenessProbe": {
              "failureThreshold": 3,
              "tcpSocket": {
                "port": 80
              },
              "initialDelaySeconds": 30,
              "periodSeconds": 60,
              "timeoutSeconds": 5
            },
            "imagePullPolicy": "IfNotPresent"
          }
        ],
        "restartPolicy": "Always",
        "dnsPolicy": "Default"
      }
  }
  """

  override def beforeAll(): Unit = {
    val k8s = k8sInit

    val pod = Json.parse(podJsonStr).as[Pod]
    k8s.create(pod).valueT
  }

  override def afterAll() = {
    val k8s           = k8sInit
    val requirements  = defaultLabels.toSeq.map { case (k, _) => LabelSelector.ExistsRequirement(k) }
    val labelSelector = LabelSelector(requirements: _*)
    val results       = k8s.deleteAllSelected[PodList](labelSelector).withTimeout()
    results.futureValue

    results.onComplete { _ =>
      k8s.close
      system.terminate().recover { case _ => () }.valueT
    }
  }

  behavior.of("PodFormat")

  it should "have the same metadata as configured" in { k8s =>
    val p = k8s.get[Pod](podName).valueT
    p.name shouldBe podName
    p.metadata.generateName shouldBe namePrefix
    p.metadata.namespace shouldBe "default"
    p.metadata.labels.exists(_ == "PodFormatSpec" -> this.suiteName) shouldBe true
  }

  it should "have the same spec containers as configured" in { k8s =>
    val maybePodSpec = k8s.get[Pod](podName).valueT.spec

    maybePodSpec should not be empty

    val containers = maybePodSpec.get.containers

    containers should not be empty
    containers.exists(_.name == containerName) shouldBe true

    val nginxContainer = containers.find(_.name == containerName).get

    nginxContainer.image shouldBe nginxImage
    nginxContainer.volumeMounts should not be empty
    nginxContainer.volumeMounts.exists(_.name == "test-empty-dir-volume") shouldBe true
    nginxContainer.livenessProbe should not be empty
    nginxContainer.resources should not be empty
    nginxContainer.resources.get.limits.exists(_ == "cpu" -> Quantity("250m")) shouldBe true

    nginxContainer.imagePullPolicy shouldBe Option(Container.PullPolicy.IfNotPresent)
  }

  it should "have the same spec pod security context as configured" in { k8s =>
    val maybePodSpec = k8s.get[Pod](podName).valueT.spec

    maybePodSpec should not be empty

    val maybeSecurityContext = maybePodSpec.get.securityContext

    maybeSecurityContext should not be empty

    val securityContext = maybeSecurityContext.get

    securityContext.fsGroup shouldBe Option(1001)
    securityContext.runAsUser shouldBe Option(1001)
    securityContext.runAsGroup shouldBe Option(1001)
    securityContext.runAsNonRoot shouldBe Option(true)
    securityContext.seccompProfile shouldBe Option(RuntimeDefaultProfile())
  }

  it should "have the same spec volumes as configured" in { k8s =>
    val maybePodSpec = k8s.get[Pod](podName).valueT.spec

    maybePodSpec should not be empty

    val volumes = maybePodSpec.get.volumes

    volumes should not be empty
    volumes.exists(_.name == "test-empty-dir-volume") shouldBe true
  }

  it should "have the same spec restartPolicy as configured" in { k8s =>
    val maybePodSpec = k8s.get[Pod](podName).valueT.spec

    maybePodSpec should not be empty

    val restartPolicy = maybePodSpec.get.restartPolicy

    restartPolicy shouldBe RestartPolicy.Always
  }

  it should "have the same spec dnsPolicy as configured" in { k8s =>
    val maybePodSpec = k8s.get[Pod](podName).valueT.spec

    maybePodSpec should not be empty

    val dnsPolicy = maybePodSpec.get.dnsPolicy

    dnsPolicy shouldBe DNSPolicy.Default
  }

}
