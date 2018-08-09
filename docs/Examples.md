# Skuber usage examples

## Basic imports

```scala
import skuber._
import skuber.json.format._


// Some standard Akka implicits that are required by the skuber v2 client API
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
implicit val dispatcher = system.dispatcher
```

## Populate cluster access configuration and initialize client

You can configure cluster in [many different ways](Configuration.md). This example
directly calls method that reads kubeconfig file at default location.
Check [kubernetes docs](https://kubernetes.io/docs/tasks/access-application-cluster/configure-access-multiple-clusters/#before-you-begin) if you don't know what is kubeconfig or where to look for it.

```scala
import api.Configuration

// assumes that Success is returned.
val cfg: Configuration = Configuration.parseKubeconfigFile().get
val k8s = k8sInit(cfg)
```

## List pods example

Here we use `k8s` client to get all pods in `kube-system` namespace:

```scala
import scala.util.{Success, Failure}
val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
listPodsRequest.onComplete {
  case Success(pods) => pods.items.foreach { p => println(p.name) }
  case Failure(e) => throw(e)
}
```

## Create deployment

This example creates a nginx service (accessed via port 30001 on each Kubernetes cluster node) that is backed by a deployment of five nginx replicas.
 Requires defining selector, container description, pod spec, deployment and service:

```scala
// Define selector
import LabelSelector.dsl._
val nginxSelector  = "app" is "nginx"

// Define nginx container
val nginxContainer = Container(name = "nginx", image = "nginx")
  .exposePort(80)

// Define nginx pod template spec
val nginxTemplate = Pod.Template.Spec
  .named("nginx")
  .addContainer(nginxContainer)
  .addLabel("app" -> "nginx")

// Define nginx deployment
import skuber.apps.v1.Deployment
val nginxDeployment = Deployment("nginx")
  .withReplicas(5)
  .withTemplate(nginxTemplate)
  .withLabelSelector(nginxSelector)

// Define nginx service
val nginxService = Service("nginx")
  .withSelector("app" -> "nginx")
  .exposeOnNodePort(30001 -> 80)

// Create the service and the deployment on the Kubernetes cluster
val createOnK8s = for {
  svc  <- k8s create nginxService
  dep  <- k8s create nginxDeployment
} yield (dep,svc)

createOnK8s.onComplete {
  case Success(_)  => println("Successfully created nginx deployment & service on Kubernetes cluster")
  case Failure(ex) => throw (new Exception("Encountered exception trying to create resources on Kubernetes cluster: ", ex))
}
```

## Safely shutdown the client

```scala
// Close client.
// This prevents any more requests being sent by the client.
k8s.close

// this closes the connection resources etc.
system.terminate
```