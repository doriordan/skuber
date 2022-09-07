
<p align="center"> <img src="skuber_logo.png" alt="skuber logo"> </p>
<p align="center">
    <a href="https://github.com/hagay3/skuber" target="_blank"  style="text-decoration:none">
        <button class="button-17" role="button" >GITHUB</button>
    </a>
</p>
 

<h1 align="center">Skuber Documentation</h1>

<p align="center"> Scala client for the <a href="https://kubernetes.io/">Kubernetes API</a>. </p>

</br>


## Quick start

This example lists pods in `kube-system` namespace:

```scala
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import scala.util.{Success, Failure}

implicit val system = ActorSystem()
implicit val dispatcher = system.dispatcher

val k8s = k8sInit
k8s.list[PodList](Some("kube-system"))
```
## Release

You can use the latest release (for 3.1, 2.12 or 2.13) by adding to your build:

```scala
libraryDependencies += "io.github.hagay3" %% "skuber" % "3.0.0"
```

## Configuration

### Kubeconfig file
```scala
import skuber._
implicit val as = ActorSystem()
val k8s: KubernetesClient = k8sInit
```
#### Overriding kube config location
By default, skuber config reads the config file from ~/.kube/config.

Set `SKUBER_CONFIG` to override that.

```bash
export SKUBER_CONFIG=file:///my_secret_location/.kube/config
```

### Manual Configuration

Set the env variables with cluster details.

```scala
import skuber.api.client.{Cluster, Context, KubernetesClient}
import java.util.Base64
import akka.actor.ActorSystem

val namespace = System.getenv("namespace")
val serverUrl = System.getenv("serverUrl")
val certificate = Base64.getDecoder.decode(System.getenv("certificate"))
val clusterName = System.getenv("clusterName")

val cluster = Cluster(server = serverUrl, certificateAuthority = Some(Right(certificate)), clusterName = Some(clusterName))
val context = Context(cluster = cluster)
val k8sConfig = Configuration(clusters = Map(clusterName -> cluster), contexts = Map(clusterName -> context)).useContext(context)

implicit val as = ActorSystem()
val k8s: KubernetesClient = k8sInit(k8sConfig)
```



### Overriding URL / Use Proxy URL

Using [kubectl proxy](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#proxy) requires setting `SKUBER_URL` env variable.
```bash
export SKUBER_URL=http://localhost:8001
```

If the cluster URL is set this way, then the `SKUBER_CONFIG` and `KUBECONFIG` environment variables are ignored by Skuber.


### Config load order

Skuber supports both out-of-cluster and in-cluster configurations.
Сonfiguration algorithm can be described as follows:

Initiailly Skuber tries out-of-cluster methods in sequence (stops on first successful):
 1. Read `SKUBER_URL` environment variable and use it as kubectl proxy url. If not set then:
 2. Read `SKUBER_CONFIG` environment variable and if is equal to:
    * `file`  - Skuber will read `~/.kube/config` and use it as configuration source
    * `proxy` - Skuber will assume that kubectl proxy running on `localhost:8080` and will use it as endpoint to cluster API
    * Otherwise treats contents of `SKUBER_CONFIG` as a file path to kubeconfig file (use it if kube config file is in custom location). If not present then:
 3. Read `KUBECONFIG` environment variable and use its contents as path to kubconfig file (similar to `SKUBER_CONFIG`)


If all above fails Skuber tries [in-cluster configuration method](https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/#accessing-the-api-from-a-pod)



### Security

When using kubeconfig files, Skuber supports standard security configuration as described below.

If the current context specifies a **TLS** connection (i.e. a `https://` URL) to the cluster server, Skuber will utilise the configured **certificate authority** to verify the server (unless the `insecure-skip-tls-verify` flag is set to true, in which case Skuber will trust the server without verification).

The above cluster configuration details can be set using [this kubectl command](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#-em-set-cluster-em-).

For client authentication **client certificates** (cert and private key pairs) can be specified for the case where TLS is in use. In addition to client certificates Skuber will use any **bearer token** or **basic authentication** credentials specified. Token or basic auth can be configured as an alternative to or in conjunction with client certificates. These client credentials can be set using [this kubectl command](https://kubernetes.io/docs/user-guide/kubectl/v1.6/#-em-set-credentials-em-).

*(Skuber loads configured server and client certificates / keys directly from the kubeconfig file (or from another location in the file system in the case where the configuration specifies a path rather than embedded data). This means there is no need to store them in the Java trust or key stores.)*


## Basic imports

```scala
import skuber._
import skuber.json.format._

import akka.actor.ActorSystem

implicit val system = ActorSystem()
implicit val dispatcher = system.dispatcher
```

## List pods example

```scala
import skuber._
import skuber.json.format._

val k8s = k8sInit
val listPodsRequest = k8s.list[PodList](namespace = Some("kube-system"))
```

## List Namespaces

```scala
import skuber._
import skuber.json.format._

val k8s = k8sInit
k8s.list()[NamespaceList]

```


## Create Pod

```scala
import skuber._
import skuber.json.format._

val podSpec     = Pod.Spec(List(Container(name = "nginx", image = "nginx")))
val pod         = Pod("nginxpod", podSpec)
val podFuture   = k8s.create(pod)

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

## API Method Summary

### Get
```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
val deployment = k8s.get[Deployment]("guestbook")
```

### List

```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
k8s.list()[NamespaceList]
```


### Create
```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
val podSpec     = Pod.Spec(List(Container(name = "nginx", image = "nginx")))
val pod         = Pod("nginxpod", podSpec)
val podFuture   = k8s.create(pod)

```

### Update
```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
val upscaledDeployment = deployment.withReplicas(5)
val depFut = k8s.update(upscaledDeployment)
depFut onSuccess { case dep =>
  println("Updated deployment, Kubernetes assigned resource version is " + dep.metadata.resourceVersion)
}
```

### Delete
```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
val rmFut = k8s delete[Deployment] "guestbook"
rmFut onSuccess { case _ => println("Deployment removed") }
```
(There is also a `deleteWithOptions` call that enables options such as propagation policy to be passed with a Delete operation.)

### Patch
Patch a Kubernetes object using a [JSON merge patch](https://tools.ietf.org/html/rfc7386):

```scala
import skuber._
import skuber.json.format._
val k8s = k8sInit
val patchStr="""{ "spec": { "replicas" : 1 } }"""
val stsFut = k8s.jsonMergePatch(myStatefulSet, patchStr)
```
See also the `PatchExamples` example. Note: There is no patch support yet for the other two (`json patch` and `strategic merge patch`) [strategies](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#patch-operations)

### Logs
Get the logs of a pod (as an Akka Streams Source):

```scala
val helloWorldLogsSource: Future[Source[ByteString, _]]  = k8s.getPodLogSource("hello-world-pod", Pod.LogQueryParams())
```

### Scale
Directly scale the number of replicas of a deployment or stateful set:

```scala
k8s.scale[StatefulSet]("database", 5)
```

(See [here](https://github.com/hagay3/skuber/blob/master/client/src/main/scala/skuber/api/client/KubernetesClient.scala) for the latest complete API documentation)


### Specific namespace
```scala
val ksysPods: Future[PodList] = k8s.list[PodList](Some("kube-system")))
```

Get lists of all Kubernetes objects of a given list kind for all namespaces in the cluster, mapped by namespace:
```scala
val allPodsMapFut: Future[Map[String, PodList]] = k8s listByNamespace[PodList]()
```
(See the ListExamples example for examples of the above list operations)



### Watch API

Kubernetes supports the ability for API clients to watch events on specified resources - as changes occur to the resource(s) on the cluster, Kubernetes sends details of the updates to the watching client. Skuber v2 now uses Akka streams for this (instead of Play iteratees as used in the Skuber v1.x releases), so the `watch[O]` API calls return `Future[Source[O]]` objects which can then be plugged into Akka flows.

```scala
import skuber._
import skuber.json.format._
import skuber.apps.v1.Deployment

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink

object WatchExamples {
  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  val k8s = k8sInit

  val frontendReplicaCountMonitor = Sink.foreach[K8SWatchEvent[Deployment]] { frontendEvent =>
    println("Current frontend replicas: " + frontendEvent._object.status.get.replicas)
  }
  for {
    frontend <- k8s.get[Deployment]("frontend")
    frontendWatch <- k8s.watch(frontend)
    done <- frontendWatch.runWith(frontendReplicaCountMonitor)
  } yield done
  // ...
}
```

The above example creates a Watch on the frontend deployment, and feeds the resulting events into an Akka sink that simply prints out the replica count from the current version of the deployment as included in each event. To test the above code, call the watchFrontendScaling method to create the watch and then separately run a number of [kubectl scale](https://kubernetes.io/docs/tutorials/kubernetes-basics/scale-interactive/) commands to set different replica counts on the frontend - for example:
```bash
kubectl scale --replicas=1 deployment/frontend
kubectl scale --replicas=10 deployment/frontend
kubectl scale --replicas=0 deployment/frontend
```

You should see updated replica counts being printed out by the sink as the scaling progresses.

The [reactive guestbook](https://github.com/hagay3/skuber/tree/master/examples/src/main/scala/skuber/examples/guestbook) example also uses the watch API to support monitoring the progress of deployment steps by watching the status of replica counts.

Additionally you can watch all events related to a specific kind - for example the following can be found in the same example:
```scala
def watchPodPhases = {
  // ...

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
  // ...
}
```
The watch can be demonstrated by calling `watchPodPhases` to start watching all pods, then in the background run the reactive guestbook example: you should see events being reported as guestbook pods are deleted, created and modified during the run.

Note that both of the examples above watch only those events which have a later resource version than the latest applicable when the watch was created - this ensures that only current events are sent to the watch, historic ones are ignored. This is often what you want, but sometimes - especially where events are being used to update important state in your application - you want to make sure you don't miss any events, even in the case where your watch has been stopped and restarted. In this case you can keep a record of the latest resource version processed in a database of some sort and then if/when the watch gets restarted you can specify that resource version in the API call to start the watch:
```scala
k8s.watch[Pod]("myPod", sinceResourceVersion=lastProcessedResourceVersion)
```

The API methods `watchContinuously` and `watchAllContinuously` are available since v2.0.10. These methods provide equivalent functionality (and type signatures) to `watch` and `watchAll` respectively, with the key difference that instead of the returned source finishing if the underlying watch request times out, these methods handle such timeouts transparently so that the application will receive new events indefinitely from the source returned by a single `watchContinuously` or `watchAllContinuously` call.

### Extensions API Group

The extensions API group traditionally contains some key types. Although in more recent versions of Kubernetes many of these have been migrated to other groups, this group is still supported and widely used.

For example, to use the `HorizontalPodAutoscaler` kind:
```scala
import skuber.ext.HorizontalPodAutoscaler
import skuber.json.ext.format._ // imports the implicit JSON formatters required to use extensions group resources
```

The currently supported extensions group kinds include `Deployment`,`ReplicaSet`,`HorizontalPodAutoscaler`, `Ingress`, `DaemonSet`, together with their list kinds.

***Deployment***

A Skuber client can create and update `Deployment` objects on the cluster to have Kubernetes automatically manage the deployment and upgrade strategy (for example rolling upgrade) of applications to the cluster.

The following example emulates that described [here](http://kubernetes.io/docs/user-guide/deployments/).

Initial creation of the deployment:
```scala
val nginxLabel = "app" -> "nginx"
val nginxContainer = Container("nginx",image="nginx:1.7.9").exposePort(80)

val nginxTemplate = Pod.Template.Spec
 .named("nginx")
 .addContainer(nginxContainer)
 .addLabel(nginxLabel)

val desiredCount = 5
val nginxDeployment = Deployment("nginx-deployment")
  .withReplicas(desiredCount)
  .withTemplate(nginxTemplate)

println("Creating nginx deployment")
val createdDeplFut = k8s create nginxDeployment
```

Use `kubectl get deployments` to see the status of the newly created Deployment, and `kubectl get rc` will show a new replication controller which manages the creation of the required pods.

Later an update can be posted - in this example the nginx version will be updated to 1.9.1:
```
val newContainer = Container("nginx",image="nginx:1.9.1").exposePort(80)
val existingDeployment = k8s get[Deployment] "nginx-deployment"
val updatedDeployment = existingDeployment.updateContainer(newContainer)
k8s update updatedDeployment
```

As no explicit deployment strategy has been selected, the default strategy will be used which will result in a rolling update of the nginx pods - again, you can use `kubectl get` commands to view the status of the deployment, replication controllers and pods as the update progresses.

The `DeploymentExamples` example runs the above steps.

If you need to support versions of Kubernetes before v1.6 then continue to use `ext.Deployment`, otherwise use `skuber.apps.<version>.Deployment` (see below) - which version to use depends on your Kubernetes version but for version 1.9 of Kubernetes (or later) use `skuber.apps.v1.Deployment`.

As the Kubernetes long-term strategy is to use more specific API groups rather then the generic extensions group, other classes in the `ext` subpackage are also likely to be migrated in future to reflect changes in Kubernetes.

***HorizontalPodAutoscaler***

A skuber client can also manage `HorizontalPodAutoscaler` objects in order to autoscale a replication controller or deployment. A fluent API approach enables minimum replica count, maximum replica count and CPU utilisation target to be readily specified. For example:
```scala
// following autoscales 'controller' with min replicas of 2, max replicas of 8
// and a target CPU utilisation of 80%
val hpas = HorizontalPodAutoscaler.scale(controller)
  .withMinReplicas(2)
  .withMaxReplicas(8)
  .withCPUTargetUtilization(80)
k8s create[HorizontalPodAutoscaler] hpas
```

***Ingress***

An ingress controller manages handling of HTTP requests from an ingress point on the Kubernetes cluster, proxying then to target services according to a user-specified set of routing rules. The rules are specified in a standard format, although different ingress controllers can utilize different underlying mechanisms to control ingress (e.g. an nginx proxy, or by configuring a hardware or cloud load balancer).

The `NginxIngress` example illustrates creation and testing of an ingress, using an nginx-based ingress controller from the Kubenretes contrib project.

***ReplicaSet***

ReplicaSet is the strategic successor of ReplicationController in the Kubernetes project. It is currently different only in supporting both equality and set based label selectors (ReplicationController only support equality-based ones).

ReplicaSet is most commonly used implicitly with Deployment types, but can be used explicitly as well - the `NginxIngress` example explicitly uses a ReplicaSet to manage the ingress controller.

### Other API groups

Aside from the `core` and `extensions` groups, more recent Kubernetes kinds tend to be defined in other, more targetted API groups. Currently skuber supports the following subpackages, each mapping to a different Kubernetes API group:

***apps***

The `apps` package supports recent versions of Workload types - use the `ext` package instead if you are on an older Kubernetes version that doesn't support the `apps` group.

The `apps` package contains subpackages for each supported version of the `apps` group: `v1beta1`,`v1beta2` and `v1`. Each subpackage contains at least `Deployment` and `StatefulSet`, while the `v1` (GA) version also contains `DaemonSet` and `ReplicaSet`.

***batch***

Contains the `Job` and `CronJob` kinds. One of the skuber examples demonstrates a client of the `Job` type.

***rbac***

Contains the `Role`,`RoleBinding`,`ClusterRole` and `ClusterRoleBinding` kinds - see the Kubernetes Role-Based Access Control documentation for details on how to use these kinds.

***apiextensions***

Currently supports one kind - the `CustomResourceDefinition` kind introduced in Kubernetes V1.7 (as successor to the now deprecated `Third Party Resources` kind, which is not supported in Skuber).

***networking***

Supports `NetworkPolicy` resources (for Kubernetes v1.7 and above) - see Kubernetes [Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/) documentation.

[Custom Resources](https://kubernetes.io/docs/concepts/api-extension/custom-resources/) are a powerful feature which enable Kubernetes clients to define and use their own custom resources to be treated in the same way as built-in kinds. They are useful for building Kubernetes operators and other advanced use cases. See the `CustomResourceSpec.scala` integration test which demonstrates how to use them in skuber.

### Custom resource
Code example for adding a resource that not exist in skuber.

Using [EventBus](https://github.com/argoproj-labs/argo-eventbus) from argocd for this example.

```scala
package skuber.examples.argo

import java.util.UUID.randomUUID
import akka.actor.ActorSystem
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.{Format, JsPath, Json}
import skuber.ResourceSpecification.{Names, Scope}
import skuber.api.client.LoggingContext
import skuber.examples.argo.EventBus.{EventBusSetList, Native, Nats, eventBusFmt, eventBusListFmt, rsDef, rsListDef}
import skuber.json.format.{ListResourceFormat, objFormat}
import skuber.{ListResource, NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, k8sInit}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

object EventBusExample extends App {
  // for testing
  // kubectl create ns argo-eventbus
  // kubectl apply -f https://raw.githubusercontent.com/argoproj-labs/argo-eventbus/stable/manifests/install.yaml

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  val k8s = k8sInit

  val eventBusResource1 = EventBus(randomUUID().toString)

  val cr = k8s.create(eventBusResource1)(eventBusFmt, rsDef, LoggingContext.lc)

  Await.result(cr, 30.seconds)
  val ls = k8s.list[EventBusSetList](Some("argo-eventbus"))(eventBusListFmt, rsListDef, LoggingContext.lc).map { eventsBusList =>
    println(eventsBusList.mkString("\n"))
  }
  Await.result(ls, 30.seconds)

  k8s.close

  Await.result(system.terminate(), 10.seconds)

}

case class EventBus(val kind: String = "EventBus",
                    override val apiVersion: String = "argoproj.io/v1alpha1",
                    val metadata: ObjectMeta = ObjectMeta(),
                    spec: Option[EventBus.Spec] = Some(EventBus.Spec(EventBus.Nats(EventBus.Native())))) extends ObjectResource {

}

object EventBus {

  val specification=NonCoreResourceSpecification(
    apiGroup = "argoproj.io",
    version = "v1alpha1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "eventbus",
      singular = "eventbus",
      kind = "EventBus",
      shortNames = List("eb")
    )
  )
  type EventBusSetList = ListResource[EventBus]
  implicit val rsDef = new ResourceDefinition[EventBus] { def spec=specification }
  implicit val rsListDef = new ResourceDefinition[EventBusSetList] { def spec=specification }

  def apply(name: String) : EventBus = EventBus(metadata=ObjectMeta(name=name))

  case class Spec(nats: Nats)

  case class Nats(native: Native)

  case class Persistence(storageClassName: String,
                         accessMode: String,
                         volumeSize: String)

  case class Native(replicas: Option[Int] = None,
                    auth: Option[String] = None,
                    persistence: Option[Persistence] = None)

  implicit val persistenceFmt: Format[Persistence] = Json.format[Persistence]

  implicit val nativeFmt: Format[Native] = Json.format[Native]
  implicit val natsFmt: Format[Nats] = Json.format[Nats]
  implicit val specFmt: Format[Spec] = Json.format[Spec]

  implicit lazy val eventBusFmt: Format[EventBus] = (
    objFormat and
      (JsPath \ "spec").formatNullable[EventBus.Spec]
    )(EventBus.apply _, unlift(EventBus.unapply))

  implicit val eventBusListFmt: Format[EventBusSetList] = ListResourceFormat[EventBus]

}
```

### Error Handling

Any call to the Skuber API methods that results in a non-OK status response from Kubernetes will cause the result of the Future returned by the method to be set to a `Failure` with an exception of class `K8SException`. This exception has a `status` field that encapsulates the data returned in the [Status](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#response-status-kind) object if Kubernetes has returned one, which it generally does when returning a non-OK status.

This exception can be handled in the appropriate manner for your use case by using the standard Scala Future failure handling mechanisms. For example, sometimes you may want to ignore a NOT_FOUND (404) error when attempting to delete an object, because it is normal and ok if it has already been deleted:

```scala
val deleteResult = (k8s delete[ReplicationController] c.name) recover {
  case ex: K8SException if (ex.status.code.contains(404)) => // ok - no action required
}
deleteResult onFailure {
  case ex: K8SException =>
    log.error("Error when deleting " + c.name + ", reason: " + ex.status.reason.getOrElse("<unknown>"))
}
```
The above code basically causes a 404 error to be silently ignored and the overall result will be a `Success`, other errors will still be propagated as `Failures` in `deleteResult`, which results in the error reason being logged.

The `Status` class is defined as follows:
```
case class Status(
  // ... metadata fields here ...
  // error details below:
  status: Option[String] = None,
  message: Option[String] = None,
  reason: Option[String] = None,
  details: Option[JsValue] = None,
  code: Option[Int] = None  // HTTP status code
)
```

# Refresh EKS (AWS) Token

[Background](#background) </br>
[Step-by-step guide](#step-by-step-guide) </br>
[Setup the environment variables](#setup-the-environment-variables) </br>
[Create IAM Role](#create-iam-role) </br>
[Create a service account](#create-a-service-account) </br>
[Create the aws-auth mapping](#create-the-aws-auth-mapping) </br>
[Skuber Code example](#skuber-code-example)

## Background
Skuber has the functionality to refresh EKS (AWS) token with an IAM role and cluster configurations.

The initiative:
* Refreshing tokens increasing k8s cluster security
* Since kubernetes v1.21 service account tokens has an expiration of 1 hour.
  https://docs.aws.amazon.com/eks/latest/userguide/kubernetes-versions.html#kubernetes-1.21


## Step-by-step guide
Pay attention to the fact that skuber can be deployed in one cluster and the cluster you want to control can be a remote cluster. </br>
In this guide I will use the following:

`SKUBER_CLUSTER` - the cluster skuber app will be deployed on. </br>
`REMOTE_CLUSTER` - the cluster that skuber will be connected to.

### Setup the environment variables
* Make sure aws cli is configured properly

```bash
export ACCOUNT_ID=$(aws sts get-caller-identity --output text --query Account)
echo $ACCOUNT_ID
```
Set the cluster name and region that need access to (`REMOTE_CLUSTER`) </br>
use `aws eks list-clusters` to see the cluster names.

```bash
export REMOTE_CLUSTER=example-cluster
export REGION=us-east-1
```


Set the cluster name which skuber app will run from (`SKUBER_CLUSTER`)

Set the namespace name which skuber app will run from

Set the oidc provider id

Set the service account name that skuber app will be attached to (we will create it later)

```bash
export SKUBER_CLUSTER=skuber-cluster
export SKUBER_NAMESPACE=skuber-namespace
export OIDC=$(aws eks describe-cluster --name $SKUBER_CLUSTER --output text --query cluster.identity.oidc.issuer | cut -d'/' -f3,4,5)
echo $OIDC
export SKUBER_SA=skuber-serviceaccount
```

### Create IAM Role

This role will allow skuber service account to assume this role. </br>

```json
cat > skuber_iam_role.json  <<EOL
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "",
            "Effect": "Allow",
            "Principal": {
                "Federated": "arn:aws:iam::${ACCOUNT_ID}:oidc-provider/${OIDC}"
            },
            "Action": "sts:AssumeRoleWithWebIdentity",
            "Condition": {
                "StringLike": {
                    "${OIDC}:sub": "system:serviceaccount:${SKUBER_NAMESPACE}:${SKUBER_SA}"
                }
            }
        }
    ]
}
EOL
```

Create and set IAM role name
```bash
export IAM_ROLE_NAME=skuber-eks
aws iam create-role \
--role-name $IAM_ROLE_NAME \
--description "Kubernetes role for skuber client" \
--assume-role-policy-document file://skuber_iam_role.json \
--output text \
--query 'Role.Arn'
```

### Create a service account
Change the context to `SKUBER_CLUSTER` and create the service account </br>

```bash
kubectl config use-context arn:aws:eks:${REGION}:${ACCOUNT_ID}:cluster/${SKUBER_CLUSTER}

kubectl apply -n $
-f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
name: ${SKUBER_SA}
EOF
```

### Create the aws-auth mapping
In order to map aws iam role to the actual kubernetes cluster permissions, we need to create a mapping:
IAM Role -> Kubernetes Permissions

For this example I'm using existing masters permissions group, its recommended to create something more specific with [RBAC](https://docs.aws.amazon.com/eks/latest/userguide/add-user-role.html).
* Create this mapping on every cluster that skuber will be able to interact with.

Change the context to `REMOTE_CLUSTER`.
```bash
kubectl config use-context arn:aws:eks:${REGION}:${ACCOUNT_ID}:cluster/${REMOTE_CLUSTER}
kubectl edit configmap aws-auth -n kube-system
```

Add the following mapping
* Replace the variables with the actual values
```yaml
    - rolearn: arn:aws:iam::$ACCOUNT_ID:role/$IAM_ROLE_NAME
      username: ci
      groups:
        - system:masters
```


### Skuber Code example
* Set the environment variables according to `REMOTE_CLUSTER`
```bash
export namespace=default
export serverUrl=$(aws eks describe-cluster --name $REMOTE_CLUSTER --output text --query cluster.endpoint)
export certificate=$(aws eks describe-cluster --name $REMOTE_CLUSTER --output text --query cluster.certificateAuthority)
export clusterName=$REMOTE_CLUSTER
export region=$AWS_REGION
```

A working example for using `AwsAuthRefreshable`
```scala
implicit private val as = ActorSystem()
implicit private val ex = as.dispatcher
val namespace = System.getenv("namespace")
val serverUrl = System.getenv("serverUrl")
val certificate = Base64.getDecoder.decode(System.getenv("certificate"))
val clusterName = System.getenv("clusterName")
val region = Regions.fromName(System.getenv("region"))
val cluster = Cluster(server = serverUrl, certificateAuthority = Some(Right(certificate)), clusterName = Some(clusterName), awsRegion = Some(region))

val context = Context(cluster = cluster, authInfo = AwsAuthRefreshable(cluster = Some(cluster)))

val k8sConfig = Configuration(clusters = Map(clusterName -> cluster), contexts = Map(clusterName -> context)).useContext(context)

val k8s: KubernetesClient = k8sInit(k8sConfig)
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
```


## Label Selectors

As alluded to above, newer API types such as ReplicaSets and Deployments support set-based as well as equality-based [label selectors](http://kubernetes.io/docs/user-guide/labels/#label-selectors).
For such types, Skuber supports a mini-DSL to build selectors:
```scala
import skuber.LabelSelector
import LabelSelector.dsl._
import skuber.apps.v1.Deployment

val sel = LabelSelector(
  "tier" is "frontend",
  "release" doesNotExist,
  "env" isNotIn List("production", "staging")
)

// now the label selector can be used with certain types
val depl = Deployment("exampleDeployment").withSelector(sel)
```

## Safely shutdown the client

```scala
// Close client.
// This prevents any more requests being sent by the client.
k8s.close

// this closes the connection resources etc.
system.terminate
```

## Data Model Overview

The Skuber data model is a representation of the Kubernetes types / kinds in Scala.

The Skuber data model for the the original core Kubernetes API group (which manages many of the most fundamental Kubernetes kinds) is defined in the top-level package, so they can be easily imported into the application:

```scala
import skuber._
```

This also imports many other common types and aliases that are generally useful.

Example of more specific core API kind imports:

```scala
import skuber.{Service,ServiceList,Pod}
```

Newer (non-core) API group classes are contained in subpackages associated with each API group. For example`skuber.ext` for the extensions API group or `skuber.rbac` for the rbac API group. Example specific imports for these kinds:

```scala
import skuber.ext.DaemonSet
import skuber.batch.{Job,CronJob}
```

In the specific case of the `apps` group, which includes Workload types such as `Deployment` and `StatefulSet`, there are subpackages for each version of the group, with `v1` being the latest:

```scala
import skuber.apps.v1.Deployment
```

The model can be divided into categories which correspond to those in the Kubernetes API:

- [Object kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#objects): These represent persistent entities in Kubernetes. All object kinds are mapped to case classes that extend the `ObjectResource` abstract class. The `ObjectResource` class defines the common fields, notably `metadata` (such as name, namespace, uid, labels etc.). The concrete classes extending ObjectResource typically define [spec and status](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#spec-and-status) nested fields whose classes are defined in the companion object (e.g. `Pod.Spec`, `ReplicationController.Status`).

- [List kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#lists-and-simple-kinds): These represent lists of object resources, and in skuber are typically returned by one of the `list` API methods. All list kinds are mapped to a `ListResource[O]` case class supporting access to basic metadata and the object kind specific items in the list.

There are thus list kinds for each object kind e.g. `ListResource[Pod]`,`ListResource[Node]`, and skuber also defines type aliases defined for each supported list kind e.g. `PodList`,`NodeList`.

- [Simple kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#lists-and-simple-kinds)

## Fluent API

A combination of generic Scala case class features and Skuber-defined fluent API methods make building out even relatively complex specifications for creation or modification on Kubernetes straightforward. The following (which can be found under the examples project) illustrates just a small part of the API:

```scala
val prodLabel = "env" -> "production"

val prodInternalSelector = Map(prodLabel, prodInternalZoneLabel)

val prodCPU = 1 // 1 KCU
val prodMem = "0.5Gi" // 0.5GiB (gibibytes)

val prodContainer=Container(name="nginx-prod", image="nginx")
  .limitCPU(prodCPU)
  .limitMemory(prodMem)
  .exposePort(80)

val internalProdTemplate = Pod.Template.Spec
  .named("nginx-prod-internal")
  .addContainer(prodContainer)
  .addLabels(prodInternalSelector)

val internalProdDeployment = Deployment("nginx-prod-int")
  .withSelector(prodInternalSelector)
  .withReplicas(8)
  .withTemplate(internalProdTemplate)
```

The unit tests in the skuber subproject contains more examples, along with the examples subproject itself.

## JSON Mapping

Kubernetes defines specific JSON representations of its types. Skuber implements Play JSON read/write [converters](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators) for mapping between the model classes and their JSON representations. These implicit converters (formatters) can be made available to the application via import statements, for example, to import all formatters for the core API group:

```scala
import skuber.json.format._
```

Similiarly, subpackages of `skuber.json` contain formatters for non-core API groups such as `rbac` etc.

Some of the more recently added subpackages in skuber - for example `apps/v1` - include the Json formatters in the companion objects of the model case classes so there is no need for these types to explicitly import their formatters.

There are many available examples of Yaml or Json representations of Kubernetes objects, for example [this file](https://github.com/kubernetes/examples/blob/master/guestbook/frontend-deployment.yaml) specifies a Deployment for the main Kubernetes project Guestbook example. To convert that Yaml representation into a Skuber `Deployment` object:

```scala
import skuber.apps.v1.Deployment

import play.api.libs.json.Json
import scala.io.Source

// NOTE: this is just a generic helper to convert from Yaml to a Json formatted string that can be parsed by skuber
def yamlToJsonString(yamlStr: String): String = {
  import com.fasterxml.jackson.databind.ObjectMapper
  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

  val yamlReader = new ObjectMapper(new YAMLFactory)
  val obj = yamlReader.readValue(yamlStr, classOf[Object])
  val jsonWriter = new ObjectMapper()
  jsonWriter.writeValueAsString(obj)
}

// Read and parse the deployment in a Skuber model
val deploymentURL = "https://raw.githubusercontent.com/kubernetes/examples/master/guestbook/frontend-deployment.yaml"
val deploymentYamlStr= Source.fromURL(deploymentURL).mkString
val deploymentJsonStr=yamlToJsonString(deploymentYamlStr)
val deployment = Json.parse(deploymentJsonStr).as[Deployment]
println("Name: " + deployment.name)
println("Replicas: " + deployment.spec.flatMap(_.replicas).getOrElse(1))
```

Equally it is straightforward to do the reverse and generate a Play Json value from a Skuber model object:

```scala
val json = Json.toJson(deployment)
```
