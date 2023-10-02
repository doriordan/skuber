package skuber.examples.auth

import org.apache.pekko.actor.ActorSystem
import org.joda.time.DateTime
import skuber.api.Configuration
import skuber.api.client.KubernetesClient
import skuber.k8sInit
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success}

/**
 * sbt examples/assembly
 * export KUBERNETES_SERVICE_HOST=kubernetes.default.svc; export KUBERNETES_SERVICE_PORT=443
 * java -cp ./examples/target/scala-2.13/skuber-examples-assembly-x.x.x.jar  skuber.examples.auth.InClusterConfigurationExample
 */
object InClusterConfigurationExample extends App {
  implicit private val as: ActorSystem = ActorSystem()
  implicit private val ex: ExecutionContextExecutor = as.dispatcher
  Configuration.inClusterConfig match {
    case Success(k8sConfig) =>
      val k8s: KubernetesClient = k8sInit(k8sConfig)(as)

      getApiVersions(0)
      getApiVersions(5)
      getApiVersions(11)

      k8s.close
      Await.result(as.terminate(), 10.seconds)
      System.exit(0)

      def getApiVersions(minutesSleep: Int): Unit = {
        println(s"Sleeping $minutesSleep minutes...")
        Thread.sleep(minutesSleep * 60 * 1000)
        println(DateTime.now)
        val apiVersions = Await.result(k8s.getServerAPIVersions, 10.seconds)
        println(apiVersions.mkString(","))
      }
    case Failure(ex) =>
      throw ex
      System.exit(0)
  }

}
