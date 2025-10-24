package examples

import org.apache.pekko.actor.ActorSystem
import skuber.model._
import skuber.json.format._
import skuber.pekkoclient.PekkoKubernetesClient

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import java.util.UUID

object CreateNginxPod {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher
    
    try {
      // Initialize Skuber Pekko client
      implicit val k8s: PekkoKubernetesClient = skuber.pekkoclient.k8sInit
      
      // Create nginx pod specification
      val podName = s"nginx-${UUID.randomUUID().toString.take(8)}"
      val nginxContainer = Container(
        name = "nginx",
        image = "nginx:1.21"
      ).exposePort(80)
      
      val nginxPodSpec = Pod.Spec(containers = List(nginxContainer))
      val podMeta = ObjectMeta(name = podName)
      val nginxPod = Pod(metadata = podMeta, spec = Some(nginxPodSpec))
      
      println(s"Creating nginx pod: $podName")
      
      // Create the pod
      val createdPod = Await.result(k8s.create(nginxPod), 30.seconds)
      println(s"✅ Pod created successfully!")
      println(s"Pod name: ${createdPod.name}")
      println(s"Pod namespace: ${createdPod.namespace}")
      println(s"Pod UID: ${createdPod.uid}")
      
      // Wait and check pod status
      println("🔄 Checking pod status...")
      Thread.sleep(5000) // Wait 5 seconds for pod to start
      
      val pod = Await.result(k8s.get[Pod](podName), 30.seconds)
      println(s"Pod phase: ${pod.status.flatMap(_.phase).getOrElse("Unknown")}")
      println(s"Pod IP: ${pod.status.flatMap(_.podIP).getOrElse("N/A")}")
      println(s"Node: ${pod.spec.map(_.nodeName).filter(_.nonEmpty).getOrElse("N/A")}")
      
      // Close client
      k8s.close()
      
    } catch {
      case ex: Exception =>
        println(s"❌ Error: ${ex.getMessage}")
        ex.printStackTrace()
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}