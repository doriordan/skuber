package skuber.examples.watch

import skuber._
import skuber.json.format._
import skuber.K8SWatchEvent
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink


/**
 * @author David O'Riordan
 */
object WatchExamples {

  def  watchFrontEndScaling = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    val k8s = k8sInit

    val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[ReplicationController]] { frontendEvent =>
      println("Current frontend replicas: " + frontendEvent._object.status.get.replicas)
    }
    for {
      frontendRC <- k8s.get[ReplicationController]("frontend")
      frontendRCWatch <- k8s.watch(frontendRC)
      done <- frontendRCWatch.runWith(frontendReplicaCountMonitor)
    } yield done

    Thread.sleep(30000) // watch for some time before closing the session
    k8s.close
    system.terminate
  }
  
  def watchPodPhases = {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    val k8s = k8sInit

    val podPhaseMonitor = Sink.foreach[K8SWatchEvent[Pod]] { podEvent =>
      val pod = podEvent._object
      val phase = pod.status flatMap { _.phase }
      println(podEvent._type + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>"))
    }

    for {
      currPodList <- k8s.list[PodList]()
      latestPodVersion = currPodList.metadata.map { _.resourceVersion }
      currPodsWatch <- k8s.watchAll[Pod](sinceResourceVersion = latestPodVersion) // ignore historic events
      done <- currPodsWatch.runWith(podPhaseMonitor)
    } yield done

    Thread.sleep(30000) // watch for some time before closing the session
    k8s.close
    system.terminate
  }
}