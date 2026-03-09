// Skuber REPL predef - auto-initializes a Pekko Kubernetes client
// This file is loaded automatically when running `./repl/amm`

import org.apache.pekko.actor.ActorSystem
import skuber.pekkoclient.*
import skuber.model.*
import skuber.model.apps.*
import skuber.model.apps.v1.*
import skuber.model.batch.*
import skuber.model.autoscaling.*
import skuber.model.autoscaling.v2.*
import skuber.json.format.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

implicit val system: ActorSystem = ActorSystem("skuber-repl")
implicit val ec: ExecutionContext = system.dispatcher

val k8s: PekkoKubernetesClient = k8sInit

println("""
============================================
 Skuber REPL - Pekko Client Initialized
============================================
 Available:
   k8s    - PekkoKubernetesClient (default namespace)
   system - ActorSystem
   ec     - ExecutionContext

 Quick examples:
   Await.result(k8s.list[PodList](), 30.seconds)
   Await.result(k8s.get[Deployment]("name"), 30.seconds)
   k8s.usingNamespace("kube-system")

 Cleanup when done:
   k8s.close()
   system.terminate()
============================================
""")
