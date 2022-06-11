package skuber.examples.auth

import akka.actor.ActorSystem
import skuber.api.Configuration
import skuber.api.client.token.AwsAuthRefreshable
import skuber.api.client.{Context, KubernetesClient}
import skuber.{PodList, k8sInit}
import scala.concurrent.Await
import scala.concurrent.duration._
import skuber.json.format._

class AwsAuthExample extends App {
  implicit private val as = ActorSystem()
  implicit private val ex = as.dispatcher

  val k8sConfigBase = Configuration()
  val k8sContextCluster = k8sConfigBase.currentContext.cluster
  val context = Context(cluster = k8sContextCluster, authInfo = AwsAuthRefreshable())
  val k8sConfig = k8sConfigBase.withCluster("cluster", k8sContextCluster).withContext("cluster", context).useContext(context)

  val k8s: KubernetesClient = k8sInit(k8sConfig)

  val pods = Await.result(k8s.listInNamespace[PodList]("demand"), 10.seconds)

  println(pods.items.map(_.name))
  val pods1 = Await.result(k8s.listInNamespace[PodList]("demand"), 10.seconds)
  println(pods1.items.map(_.name))

  Thread.sleep(4000)
  val pods2 = Await.result(k8s.listInNamespace[PodList]("demand"), 10.seconds)
  println(pods2.items.map(_.name))

  k8s.close
  as.terminate().foreach { f =>
    System.exit(1)
  }
}
