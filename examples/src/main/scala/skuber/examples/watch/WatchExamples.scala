package skuber.examples.watch

import skuber._
import skuber.json.format._
import skuber.K8SWatchEvent
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import scala.concurrent.ExecutionContextExecutor


/**
 * @author David O'Riordan
 */
object WatchExamples extends App {

  implicit val system: ActorSystem = ActorSystem("watch")
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  implicit val k8s: K8SRequestContext = k8sInit

  def  watchFrontEndScaling = {

    val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[ReplicationController]] { frontendEvent =>
      println("Current frontend replicas: " + frontendEvent._object.status.get.replicas)
    }
    for {
      frontendRC <- k8s.get[ReplicationController]("frontend")
      frontendRCWatch <- k8s.watch(frontendRC)
      done <- frontendRCWatch.runWith(frontendReplicaCountMonitor)
    } yield done
  }
  
  def watchPodPhases = {

    val podPhaseMonitor = Sink.foreach[K8SWatchEvent[Pod]] { podEvent =>
      val pod = podEvent._object
      val phase = pod.status flatMap { _.phase }
      println(podEvent._type.toString + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>").toString)
    }

    for {
      currPodList <- k8s.list[PodList]()
      latestPodVersion = currPodList.metadata.map { _.resourceVersion }
      currPodsWatch <- k8s.watchAll[Pod](sinceResourceVersion = latestPodVersion) // ignore historic events
      done <- currPodsWatch.runWith(podPhaseMonitor)
    } yield done
  }

  // Note: run appropriate kubectl commands (like 'run') or an example like gueestbook to see events being output
  watchPodPhases
  watchFrontEndScaling

  Thread.sleep(1200000) // watch for a lengthy time before closing the session
  k8s.close
  system.terminate().foreach { f =>
    System.exit(0)
  }
}