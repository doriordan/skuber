package skuber.examples.watch

import skuber.model.{Pod, PodList, ReplicationController}
import skuber.json.format._
import skuber.api.client.K8SWatchEvent
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import skuber.pekkoclient.{PekkoKubernetesClient, k8sInit}

import scala.concurrent.ExecutionContext

/**
 * @author David O'Riordan
 */
object WatchExamples extends App {

  implicit val system: ActorSystem = ActorSystem("watch")
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val k8s: PekkoKubernetesClient = k8sInit

  def  watchFrontEndScaling = {

    val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[ReplicationController]] { frontendEvent =>
      println("Current frontend replicas: " + frontendEvent._object.status.get.replicas)
    }
    for {
      frontendRC <- k8s.get[ReplicationController]("frontend")
      frontendRCWatch = k8s.getWatcher[ReplicationController].watchObjectSinceVersion(frontendRC.name, frontendRC.resourceVersion)
      done <- frontendRCWatch.runWith(frontendReplicaCountMonitor)
    } yield done
  }
  
  def watchPodPhases = {

    val podPhaseMonitor = Sink.foreach[K8SWatchEvent[Pod]] { podEvent =>
      val pod = podEvent._object
      val phase = pod.status flatMap { _.phase }
      println(s"""${podEvent._type} => Pod '${pod.name}' .. phase = ${phase.getOrElse("<None>")}""")
    }

    for {
      currPodList <- k8s.list[PodList]()
      latestPodVersion = currPodList.metadata.map { _.resourceVersion }
      currPodsWatch = k8s.getWatcher[Pod].watchSinceVersion(latestPodVersion.getOrElse("")) // ignore historic events
      done <- currPodsWatch.runWith(podPhaseMonitor)
    } yield done
  }

  // Note: run appropriate kubectl commands (like 'run') or an example like guestbook to see events being output
  watchPodPhases
  watchFrontEndScaling

  Thread.sleep(1200000) // watch for a lengthy time before closing the session
  k8s.close()
  system.terminate().foreach { f =>
    System.exit(0)
  }
}