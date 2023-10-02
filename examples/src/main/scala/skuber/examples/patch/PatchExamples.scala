package skuber.examples.patch

import org.apache.pekko.actor.ActorSystem
import skuber._
import skuber.json.format._
import skuber.apps.v1beta1.StatefulSet

import scala.concurrent.Await
import scala.concurrent.duration.Duration.Inf
import play.api.libs.json.{Format, Json}
import skuber.api.patch.JsonMergePatch
/**
 * @author David O'Riordan
 * 
 * Some simple examples of patching Kubernetes resources
 * This initial version scales an example StatefulSet by updatng its replica count using a basic JSON merge patch
 * (see https://tools.ietf.org/html/rfc7386)
 */
object PatchExamples extends App {

  // API patch method.
  // This will change the replica count on the statefulset causing it to scale accordingly.
  case class ReplicaSpec(replicas: Int)
  case class ReplicaPatch(spec: ReplicaSpec) extends JsonMergePatch

  implicit val rsFmt: Format[ReplicaSpec] = Json.format[ReplicaSpec]
  implicit val rpFmt: Format[ReplicaPatch] = Json.format[ReplicaPatch]

  val statefulSetName = "nginx-patch-sts"

  def scaleNginx = {

    val nginxContainer = Container("nginx",image="nginx").exposePort(80)
    val nginxBaseSpec = Pod.Template.Spec().addContainer(nginxContainer)


    val nginxStsLabels=Map("patch-example" -> "statefulset")
    val nginxStsSel=LabelSelector(LabelSelector.IsEqualRequirement("patch-example","statefulset"))
    val nginxStsSpec=nginxBaseSpec.addLabels(nginxStsLabels)
    val nginxStatefulSet= StatefulSet(statefulSetName)
      .withReplicas(4)
      .withServiceName(statefulSetName)
      .withLabelSelector(nginxStsSel)
      .withTemplate(nginxStsSpec)

    // StatefulSet needs a headless service
    val nginxStsService: Service=Service(nginxStatefulSet.spec.get.serviceName.get, nginxStsLabels, 80).isHeadless

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher

    val k8s = k8sInit

    println("Creating nginx stateful set")
    val createdStsFut = for {
      svc <- k8s create nginxStsService
      sts <- k8s create nginxStatefulSet
    } yield sts
   
    val stsFut = createdStsFut recoverWith {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the stateful set or service already exists - retrieving latest version")
        k8s.get[StatefulSet](nginxStatefulSet.name)
      }
    }

    // Wait for stateful set creation before proceeding
    val sts = Await.result(stsFut, Inf)
    println("waiting two minutes to allow Stateful Set creation to complete before patching it")
    Thread.sleep(120000)


    println("Patching stateful set to assign replica count of 1")

    // Create the Patch
    val singleReplicaPatch=ReplicaPatch(ReplicaSpec(1))
    val singleReplicaPatchJson=Json.toJson(singleReplicaPatch)
    val singleReplicaPatchJsonStr=singleReplicaPatchJson.toString

    // Send the Patch to the statefulset on Kubernetes
    val patchedStsFut = k8s.patch[ReplicaPatch, StatefulSet](statefulSetName, singleReplicaPatch)

    val patchedSts = Await.result(patchedStsFut, Inf)
    println(s"Patched statefulset now has a desired replica count of ${patchedSts.spec.get.replicas}")
    println("waiting 5 minutes to allow scaling to be observed before cleaning up")
    Thread.sleep(300000)
    println("will now delete StatefulSet and its service")
    val cleanupRequested= for {
      sts <- k8s.deleteWithOptions[StatefulSet](nginxStatefulSet.name, DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
      done <- k8s.delete[Service](nginxStsService.name)
    } yield done


    Await.ready(cleanupRequested, Inf)
    println("Finishing up")
    k8s.close
    system.terminate()
  }
  scaleNginx
}
