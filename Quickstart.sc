/* This is a quick start ammoninte script fot the impatient
   Script assumes that you have a running kubernetes cluster or minikube running
   and kube config file located in default location 

   Cluster access configuration is parsed to `cfg` val
   Client val is called `k8s`
*/
import $ivy.`io.skuber::skuber:2.0.10`, skuber._, skuber.json.format._

import org.apache.pekko.actor.ActorSystem
import api.Configuration
import scala.concurrent.Future
import scala.util.{Success, Failure}
import skuber.apps.v1.Deployment

// Some standard Akka implicits that are required by the skuber v2 client API
implicit val system = ActorSystem()
implicit val dispatcher = system.dispatcher

val cfg: Configuration = api.Configuration.parseKubeconfigFile().get
println("'cfg' <= configuration parsed")

// Initialise skuber client
val k8s = k8sInit(cfg)
println("'k8s' <= client initialized")



