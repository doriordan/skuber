package skuber.examples.ingress

import java.net.HttpURLConnection
import org.apache.pekko.actor.ActorSystem
import skuber._
import skuber.ext.ReplicaSet
import skuber.networking.Ingress
import scala.annotation.tailrec

//  the extensions group kinds used in this example

import skuber.json.format._
import skuber.json.ext.format._
import skuber.json.networking.format._

import scala.concurrent.Future

/**
  * @author David O'Riordan
  * This Skuber creates and tests an nginx-based Ingress by emulating the steps described at
  * https://github.com/kubernetes/contrib/tree/master/ingress/controllers/nginx
  */

object NginxIngress extends App {

  val httpPort=80
  val httpsPort=443

  val nodeIngressHttpPort=30080
  val nodeIngressHttpsPort=30443

  run

  /**
    * Builds an nginx ingress controller object like this one:
    * https://github.com/kubernetes/contrib/blob/master/ingress/controllers/nginx/examples/default/rc-default.yaml
    * with the notable exception that this builds a ReplicaSet rather than a ReplicationController, and exposes it via
    * a Service with a NodePort which makes amongst other things it a bt easier to automatically test.
    *
    * @return a replica set object that can be created on Kubernetes to get the controller running on the cluster.
    */
  def buildIngressController: (Service, ReplicaSet) = {

    val replicase=1
    val name="skuber-nginx-ing-ctrlr"
    val ingressControllerPodLabel = "skuber-example-app" -> "nginx-ingress-lb"
    val controllerImage="gcr.io/google_containers/nginx-ingress-controller:0.7"

    val nginxContainer = Container(name=name, image=controllerImage)
        .withImagePullPolicy(Some(Container.PullPolicy.Always))
        .withHttpLivenessProbe("/healthz",10249,initialDelaySeconds=30,timeoutSeconds=5)
        .setEnvVarFromField("POD_NAME", "metadata.name")
        .setEnvVarFromField("POD_NAMESPACE", "metadata.namespace")
        .exposePort(httpPort)
        .exposePort(httpsPort)
        .withArgs("/nginx-ingress-controller",
          "--default-backend-service=default/default-http-backend")

    val podSpec = Pod.Spec()
        .addContainer(nginxContainer)
        .withTerminationGracePeriodSeconds(60)

    val rset = ReplicaSet(name=name, podSpec=podSpec, labels=Map(ingressControllerPodLabel))

    val svc = Service(name)
        .withSelector(ingressControllerPodLabel)
        .exposeOnNodePort(nodeIngressHttpPort -> httpPort, "http")
        .exposeOnNodePort(nodeIngressHttpsPort -> httpsPort, "https")
    (svc,rset)
  }

  /**
    * Builds a simple ingress object that encapsulates some rules for routing HTTP traffic coming into the cluster
    * Similar to https://github.com/kubernetes/contrib/blob/master/ingress/controllers/nginx/examples/ingress.yaml
    *
    * @return
    */
  def buildIngress: Ingress = {
    Ingress("echomap")
        .addHttpRule("foo.bar.com", Map("/foo" -> "echoheaders-x:80"))
        .addHttpRule("bar.baz.com", Map("/bar" -> "echoheaders-y:80", "/foo" -> "echoheaders-x:80"))
  }

  /*
   * Builds a service that simply always returns 404, to be used as default backend for the ingress
   */
  def buildDefaultBackendService: (Service, ReplicaSet) = {

    val backendPodLabel = "app" -> "default-http-backend"

    val container = Container(name="default-http-backend",image="gcr.io/google_containers/defaultbackend:1.0")
        .withHttpLivenessProbe(path="/healthz", port = 80, initialDelaySeconds = 30, timeoutSeconds = 5)
        .exposePort(8080)
        .limitCPU("10m")
        .limitMemory("20Mi")
        .requestCPU("10m")
        .requestMemory("20Mi")

    val podSpec = Pod.Spec()
        .addContainer(container)
        .withTerminationGracePeriodSeconds(60)

    val rset = ReplicaSet(name="default-http-backend", podSpec=podSpec, labels=Map(backendPodLabel))
    val svc = Service("default-http-backend")
        .withSelector(backendPodLabel)
        .exposeOnPort(Service.Port(port=80, targetPort=Some(8080)))

    (svc,rset)
  }

  def buildEchoheadersServices: (List[Service], ReplicaSet) = {

    val echoHeadersPodLabel= "app" -> "echoheaders"

    val container = Container(name = "echoheaders", image = "gcr.io/google_containers/echoserver:1.4")
        .exposePort(8080)

    val podSpec = Pod.Spec().addContainer(container)

    val rset = ReplicaSet(name="echoheaders", podSpec=podSpec, labels=Map(echoHeadersPodLabel))
        .withReplicas(1)

    val echoheadersX = Service("echoheaders-x")
        .withSelector(echoHeadersPodLabel)
        .exposeOnPort(Service.Port(port = 80, targetPort = Some(8080)))

    val echoheadersY = Service("echoheaders-y")
        .withSelector(echoHeadersPodLabel)
        .exposeOnPort(Service.Port(port = 80, targetPort = Some(8080)))

    (List(echoheadersX, echoheadersY), rset)
  }

  def testIngress(ingress: Ingress)(implicit k8s: K8SRequestContext, ec: scala.concurrent.ExecutionContext) = {
    // we test the ingress simply by sending a GET with an appropriate Host header

    // for this simple use case we leverage the built in Java URL / HTTP support, iwth code brutally
    // copied and pasted from examples on t'Interweb - coz life is too short...

    // firstly we have to do this to allow the Host header to be set in the request...
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

    def httpGet(ipAddress: String, port: Int, path: String, host: String) : String  = {
      import java.io.BufferedReader
      import java.io.InputStreamReader
      import java.net.URL
      import java.nio.charset.Charset

      val sb = new StringBuilder()
      val urlStr = "http://" + ipAddress + ":" + port + "/" + path
      try {
        val url = new URL(urlStr)
        val urlConn = url.openConnection().asInstanceOf[HttpURLConnection]
        if (urlConn != null) {
          urlConn.setReadTimeout(60 * 1000)
          urlConn.setRequestProperty("Host", host)
          val responseCode = urlConn.getResponseCode
          if (responseCode == 200) {
            val reader = new InputStreamReader(urlConn.getInputStream)
            val bufferedReader = new BufferedReader(reader)
            if (bufferedReader != null) {
              @tailrec def munch: Unit = {
                bufferedReader.read match {
                  case cp if cp != -1 => {
                    sb.append(cp.toChar)
                    munch
                  }
                  case _ =>
                }
              }
              munch
              bufferedReader.close()
            }
          }
          else {
            System.err.println("...received " + responseCode + " status")
            throw new RuntimeException("Non-ok status received while calling URL: " + urlStr)
          }
        }
      } catch {
        case ex: Exception => throw new RuntimeException("Exception while calling URL:" + urlStr, ex)
      }

      return sb.toString()
    }

    // allow for a number of retries , as the
    val retryIntervalSeconds = 3
    val retryCount = 10

    println("The next step tests the ingress rule by sending a valid HTTP request to the ingress")
    println(" *** The address at which the ingress can be reached depends partly on your environment")
    println(" *** By default we use an address we obtain from the load balaancer information on the status field ")
    println(" *** of the Ingress we created, if none available it defaults to 127.0.0.1")
    println(" *** You can override the address if it looks wrong (e.g. if using minikube, use output of 'minikube ip')")

    val lbAddressOpt = for {
      status <- ingress.status
      lb <- status.loadBalancer
      headIng <- lb.ingress.headOption
      addr <- headIng.ip.orElse((headIng.hostName))
    } yield addr
    val lbAddress=lbAddressOpt.getOrElse("127.0.0.1")

    print("Enter Ingress Address [" + lbAddress + "]:")
    val enteredAddress = scala.io.StdIn.readLine()
    val address = enteredAddress match {
      case "" => lbAddress
      case _ => enteredAddress
    }

    import scala.annotation.tailrec
    @tailrec
    def attempt(remainingAttempts: Int): Boolean =
    {
      try {
       println("Testing...attempting to GET from a path that ingress should route to echoheaders service")
        val response = httpGet(ipAddress = address, port = nodeIngressHttpPort, path = "foo", host = "foo.bar.com")
        println("Testing...successfully got response: \n" + response)
        true
      } catch {
        case ex: Throwable =>
          println("Testing...attempt failed: " + ex.getMessage)
          if (remainingAttempts > 0) {
            Thread.sleep(retryIntervalSeconds * 1000)
            attempt(remainingAttempts - 1)
          }
          else {
            println(("Testing...exceeded max retry count, giving up"))
            false
          }
      }
    }
    attempt(retryCount)
  }

  def run = {

    implicit val system = ActorSystem()
    implicit val dispatcher = system.dispatcher

    implicit val k8s: K8SRequestContext = k8sInit

    // build the resources
    val be = buildDefaultBackendService
    val beSvc = be._1
    val beRset = be._2
    val es = buildEchoheadersServices
    val esSvcs = es._1
    val esRset = es._2
    val ingCtrlr= buildIngressController
    val ingCtrlSvc = ingCtrlr._1
    val ingCtrlRset = ingCtrlr._2
    val ingressSpec = buildIngress

    // wrappers for creating resources which processes 409 (resource already exists) or 422 (probably port already allocated)
    // so that the example continues to run.
    def ignore409: PartialFunction[Throwable, Any] = {
      case ex: K8SException if (ex.status.code.contains(409)) => {
        println("It seems the resource  already exists - continuing")

      }
    }
    def createRS(rs: ReplicaSet) = (k8s create rs) recover ignore409
    def createSvc(svc: Service) = (k8s create svc) recover ignore409

    // In the case of the ingress we update it if it already exists, as it is a resource likely to be modified
    // more often than the others in this example
    def updateIf409(ing: Ingress): PartialFunction[Throwable, Future[Ingress]] = {
      case ex: K8SException if ex.status.code.contains(409) => {
        println("Ingress already exists - updating to current rules and continuing")
        k8s.get[Ingress](ing.name).flatMap { curr =>
          println("...retrieved ingress, now updating the rules")
          val updated = ing.copy(metadata = curr.metadata) // copies latest resource version for update
          k8s update updated
        }
      }
    }

    def createIng(ing: Ingress): Future[Ingress] = (k8s create ing) recoverWith updateIf409(ing)

    // helpers for creating the resources on the cluster
    def createNonIngressResources = Future.sequence(List(createSvc(beSvc),
          createRS(beRset),
          createRS(esRset)))
    def createIngressController = Future.sequence(List(createSvc(ingCtrlSvc),
          createRS(ingCtrlRset)))
    def createIngress = createIng(ingressSpec)

    // create the resources in this order:
    // 1. Create the non-ingress resources (default backend service, echoheaders services)
    // 2. Create the ingress controller service
    // 3. Check/wait until the controller is running
    // 4. Create the ingress rules
    // 5. Test the ingress / rules by performing an appropriate GET to a node/port that exposes the ingress service
    println("Creating required services on cluster")
    val ingress: Future[Ingress] = for {
      _ <- createNonIngressResources
      _ <- createIngressController
      _ <- Future.successful(println("Waiting for 10 seconds to enable ingress controller to start..."))
      _ <- Future.successful(Thread.sleep(10000))
      _ <- Future.successful(println("now creating / updating the ingress rules"))
      ing <- createIngress
    } yield ing

    val done = for {
      ing <- ingress
      succeeded = testIngress(ing)
    } yield succeeded

    done map { success =>
      if (success)
          println("Successful.")
        else
          println("Failed - test unsuccessful.")
    }

    done.failed.foreach {
      case ex: K8SException => println("*** FAILED with status=" + ex.status)
      case other => println("FAILED: " + other.getMessage)
    }
  }
}
