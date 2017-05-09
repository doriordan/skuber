package skuber.examples.list

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import skuber.Pod.Phase
import skuber._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import skuber.json.format._
  
/**
 * @author David O'Riordan
 * 
 * A couple fo simple examples of getting lists of objects across all namespaces
 * 
 */
object ListExamples extends App {

  private def listPods(pods: List[Pod]) = {

    System.out.println("")
    System.out.println("POD                                               NAMESPACE           PHASE")
    System.out.println("===                                               =========           =======")

    pods.map { pod: Pod =>
      val name = pod.name
      val ns = pod.namespace
      val phaseOpt = for {
        status <- pod.status
        phase <- status.phase
      } yield phase
      val phase = phaseOpt.getOrElse("Not set")

      System.out.println(f"${name}%-50s${ns}%-20s${phase}")
    }
  }

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val k8s = k8sInit

  System.out.println("\nGetting list of pods in namespace of current context ==>")

  val currNsPods: Future[PodList] = k8s list[PodList]()
  val printCurrNsPods = currNsPods map { podList => listPods(podList.items) }
  printCurrNsPods onFailure { case ex: Exception => System.err.println("Failed => " + ex) }

  Await.ready(printCurrNsPods, 30 seconds)

  System.out.println("\nGetting lists of pods in 'kube-system' namespace ==>")

  val ksysPods: Future[PodList] = k8s listInNamespace[PodList]("kube-system")
  val printKSysPods = ksysPods map { podList => listPods(podList.items) }
  printKSysPods onFailure { case ex: Exception => System.err.println("Failed => " + ex) }

  Await.ready(printKSysPods, 30 seconds)

  System.out.println("\nGetting lists of pods in all namespaces in the cluster ==>")

  val allPodsMapFut: Future[Map[String, PodList]] = k8s listByNamespace[PodList]()
  val allPods: Future[List[Pod]] = allPodsMapFut map { allPodsMap =>
    allPodsMap.values.flatMap(_.items).toList
  }
  val printAllPods = allPods map { pods=> listPods(pods) }
  printAllPods onFailure { case ex: Exception => System.err.println("Failed => " + ex) }

  Await.ready(printAllPods, 30 seconds)

  k8s.close
}