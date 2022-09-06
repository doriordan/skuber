package skuber.examples.auth

import java.util.Base64
import akka.actor.ActorSystem
import com.amazonaws.regions.Regions
import org.joda.time.DateTime
import skuber.api.Configuration
import skuber.api.client.token.AwsAuthRefreshable
import skuber.api.client.{Cluster, Context, KubernetesClient}
import skuber.json.format._
import skuber.{PodList, k8sInit}
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

object AwsAuthExample extends App {
  implicit private val as: ActorSystem = ActorSystem()
  implicit private val ex: ExecutionContextExecutor = as.dispatcher
  val namespace = System.getenv("namespace")
  val serverUrl = System.getenv("serverUrl")
  val certificate = Base64.getDecoder.decode(System.getenv("certificate"))
  val clusterName = System.getenv("clusterName")
  val region = Regions.fromName(System.getenv("region"))
  val cluster = Cluster(server = serverUrl, certificateAuthority = Some(Right(certificate)), clusterName = Some(clusterName), awsRegion = Some(region))

  val context = Context(cluster = cluster, authInfo = AwsAuthRefreshable(cluster = Some(cluster)))

  val k8sConfig = Configuration(clusters = Map(clusterName -> cluster), contexts = Map(clusterName -> context)).useContext(context)

  val k8s: KubernetesClient = k8sInit(k8sConfig)(as)
  listPods(namespace, 0)
  listPods(namespace, 5)
  listPods(namespace, 11)

  k8s.close
  Await.result(as.terminate(), 10.seconds)
  System.exit(0)

  def listPods(namespace: String, minutesSleep: Int): Unit = {
    println(s"Sleeping $minutesSleep minutes...")
    Thread.sleep(minutesSleep * 60 * 1000)
    println(DateTime.now)
    val pods = Await.result(k8s.listInNamespace[PodList](namespace), 10.seconds)
    println(pods.items.map(_.name))
  }
}
