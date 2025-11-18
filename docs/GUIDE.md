# Skuber Programming Guide

Note: This guide is for Skuber 3 - see [here](skuber2/GUIDE.md) for the Skuber 2 guide.

This guide assumes a working knowledge of Kubernetes concepts.

## Data Model Overview

The Skuber data model is a representation of the Kubernetes types / kinds in Scala.

This model is contained in the skuber core library which can be added to you project as follows (see main [README ](../README.md) for latest Skuber version details):
```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "<skuber version>"
```

The Skuber data model for the original core Kubernetes API group (which manages many of the most fundamental Kubernetes kinds) is defined in the `skuber.model` package, so they can be easily imported into your application:

```scala
import skuber.model._
```

This also imports many other common types and aliases that are generally useful.

Example of more specific core API kind imports:

```scala   
import skuber.model.{Service,ServiceList,Pod}   
```

Newer (non-core) API group classes are contained in subpackages generally named after the API group. For example`skuber.model.batch` for the `batch` API group or `skuber.model.rbac` for the `rbac` API group. Example specific imports for these kinds:

```scala
import skuber.model.rbac.Role
import skuber.model.batch.{Job,CronJob}
```

In the specific case of the `apps` group, which includes Workload types such as `Deployment`, `StatefulSet` and `DaemonSet`, there are subpackages for each version of the group, with `v1` being the one that should be used unless your Kubernetes version is very old:

```scala
import skuber.apps.v1.Deployment
```

The model can be divided into categories which correspond to those in the Kubernetes API:

- [Object kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#objects): These represent persistent entities in Kubernetes. All object kinds are mapped to case classes that extend the `ObjectResource` abstract class. The `ObjectResource` class defines the common fields, notably `metadata` (such as name, namespace, uid, labels etc.). The concrete classes extending ObjectResource by convention usually define [spec and status](https://kubernetes.io/docs/concepts/overview/working-with-objects/#object-spec-and-status) nested fields whose classes are defined in the companion object (e.g. `Pod.Spec`, `ReplicationController.Status`).

- [List kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#lists-and-simple-kinds): These represent lists of object resources, and in skuber are typically returned by one of the `list` API methods. All list kinds are mapped to a `ListResource[O]` case class supporting access to basic metadata and the object kind specific items in the list. 

There are thus list kinds for each object kind e.g. `ListResource[Pod]`,`ListResource[Node]`, and skuber also defines type aliases defined for each supported list kind e.g. `PodList`,`NodeList`.   

- [Simple kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#lists-and-simple-kinds) 

## JSON Mapping

Kubernetes defines specific JSON representations of its resource kinds. Skuber implements Play JSON read/write [converters](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators) for mapping between the model classes and their JSON representations. These implicit converters (formatters) can be made available to your application via import statements, for example, to import all formatters for the core API group:

```scala 
import skuber.json.format._
```

Similarly, subpackages of `skuber.json` contain formatters for non-core API groups such as `rbac` etc.

Some of the more recently added subpackages in skuber - for example `apps/v1` - include the Json formatters in the companion objects of the model case classes so there is no need for these types to explicitly import their formatters.

The appropriate JSON formatters for the resource kinds used by the application will need to be imported in order to use them with the Kubernetes client, as Skuber uses them to read/write the data in the remote calls to Kubernetes.

Here is an example of creating a Skuber model deployment object from its JSON representation.

```scala
import skuber.model.apps.v1.Deployment

import play.api.libs.json.Json   
import scala.io.Source

def yamlToJsonString(str: String): String = ??? // convert YAML to JSON 

// Read and parse the deployment in a Skuber model
val deploymentURL = "https://raw.githubusercontent.com/kubernetes/examples/master/web/guestbook/frontend-deployment.yaml"
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

## Using the Kubernetes Client

### Creating a client

In order to actually use Skuber to interact with Kubernetes, a Kubernetes client will first need to be created.

Skuber actually supports two concrete implementations of the same Kubernetes client Scala API - one that utilises Pekko under the hood and another that uses Akka under the hood. 

If in doubt use the Pekko-based client, as is used below. This requires adding the following dependency to the application build:
```sbt
libraryDependencies += "io.skuber" %% "skuber-pekko" % "<skuber version>"
```

See the [migration guide](MIGRATION2to3.md) for details on how to use the Akka client instead.

```scala
import org.apache.pekko.actor.ActorSystem
import skuber.pekkoclient._

implicit val system: ActorSystem = ActorSystem()
implicit val dispatcher: ExecutionContext = system.dispatcher

val k8s = k8sInit
```

Here the `k8sInit` call returns a concrete client that supports the API methods described below. 

The simple steps required to create a Kubernetes client:

- import and create an implicit Pekko `ActorSystem` - this will be used by the client under the hood to manage Kubernetes connections, requests and streaming operations.
- import the `k8sInit` factory methods for creating concrete Kubernetes clients from `skuber.pekkoclient`
- call `k8sInit` to create the client, ensuring the implicit actor system is in scope

### Basic client API Usage

These are the basic steps to use the Skuber client API:

- Import the model definitions and associated JSON formatters for your required resource kinds from the appropriate package(s) as described above.
- Create a Kubernetes client as described above
- Invoke the appropriate API method after creating any required resource for the request first
- The requests generally return their results (usually object or list kinds) asynchronously via `Future`s.

For example, the following creates an nginx deployment on our Kubernetes cluster:
```scala
import skuber.model.apps.v1.Deployment // no need to explicitly import JSON formatter for Deployment type
    
import org.apache.pekko.actor.ActorSystem
import skuber.pekkoclient._ // import the Pekko based Kubernetes client

implicit val system: ActorSystem = ActorSystem()
implicit val dispatcher: ExecutionContext = system.dispatcher
    
val k8s = k8sInit // create a client

val nginxDeployment: Deployment = buildNginxDeployment("nginx") 
val deplFuture = k8s.create(deployment) // create the deployment resource on the cluster

deplFut.foreach { depl => println(s"Deployment created: ${depl.name}")}
```

The `buildNginxDeployment` method in this example could be implemented as [shown here](#building-resources).

Each Kubernetes client is associated with a specific default namespace - normally just `default`. This means unless otherwise indicated most operations on the client are scoped to that namespace. There is however an extremely lightweight way of creating a new client targeting a different namespace:

`val myOtherK8s = k8s.usingNamespace("myOtherNamespace")`

When finished making requests the application should call `close` on the Kubernetes client. The application should also explicitly perform any required actor system cleanup, e.g. `system.terminate()`

```scala  
k8s.close
system.terminate
```     
### API Method Summary

(See [here](https://github.com/doriordan/skuber/blob/master/client/src/main/scala/skuber/api/client/KubernetesClient.scala) for the latest complete API documentation)

Create a resource on Kubernetes from a Skuber object kind:
```scala
val deployment: Deployment = buildNginxDeployemnt("nginx")
val depFut = k8s.create(deployment)
depFut.foreach { dep => 
  println("Created deployment, Kubernetes assigned resource version is " dep.metadata.resourceVersion) 
}
```

Get a Kubernetes object kind resource by type and name:
```scala
val depFut = k8s.get[Deployment]("guestbook")
depFut.foreach { dep => println("Current replica count = " + dep.status.get.replicas) }
```

Get a list of all Kubernetes objects of a given list kind in the current namespace:
```scala
val depListFut = k8s.list[DeploymentList]()
depListFut.foreach{ depList => depList.items.foreach { dep => println(dep.name) } }
```

As above, but for a specified namespace:
```scala
val ksysPods: Future[PodList] = k8s.listInNamespace[PodList]("kube-system")
```

Dynamically switch specified namespace for any operation
```scala
val depListFut = k8s.usingNamespace("my-namespace").list[DeploymentList]()
```

Get lists of all Kubernetes objects of a given list kind for all namespaces in the cluster, mapped by namespace:
```scala    
val allPodsMapFut: Future[Map[String, PodList]] = k8s.listByNamespace[PodList]()
```
(See the ListExamples example for examples of the above list operations)

Update a Kubernetes object kind resource:
```scala
val upscaledDeployment = deployment.withReplicas(5)
val depFut = k8s.update(upscaledDeployment)
depFut.foreach { dep => 
  println("Updated deployment, Kubernetes assigned resource version is " + dep.metadata.resourceVersion) 
}
```

Delete a Kubernetes object:
```scala
val rmFut = k8s.delete[Deployment]("guestbook")
rmFut.foreach { _ => println("Deployment removed") }
```
(There is also a `deleteWithOptions` call that enables options such as propagation policy to be passed with a Delete operation.)

Patch a Kubernetes object using different [patch strategies](https://kubernetes.io/docs/reference/using-api/api-concepts/#patch-and-apply) (Skuber supports JSON Patch, JSON Merge Patch and Strategic Merge Patch strategies):
```scala   
val patchData = MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None)
k8s.patch[MetadataPatch, Pod](nginxPodName, patchData)

val patchDataStrategicMerge = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = StrategicMergePatchStrategy)
k8s.patch[MetadataPatch, Pod](nginxPodName, patchDataStrategicMerge)

val patchDataJsonMerge = new MetadataPatch(labels = Some(Map("foo" -> randomString)), annotations = None, strategy = JsonMergePatchStrategy)
k8s.patch[MetadataPatch, Pod](nginxPodName, patchDataJsonMerge)

val jsonPatchData = JsonPatch(List(
  JsonPatchOperation.Add("/metadata/labels/foo", randomString),
  JsonPatchOperation.Add("/metadata/annotations", randomString),
  JsonPatchOperation.Remove("/metadata/annotations"),
))
k8s.patch[JsonPatch, Pod](nginxPodName, jsonPatchData)
```
See also the `PatchExamples` example.

Get the logs of a pod (as a Pekko or Akka Streams Source):
```scala
    ...
    import org.apache.pekko.streams.Source
    ...
    val helloWorldLogsSource: Future[Source[ByteString, _]]  = k8s.getPodLogSource("hello-world-pod", Pod.LogQueryParams())
```

Execute a command in a pod, streaming the output of the command to a Pekko or Akka Sink
```scala
      def closeAfter(duration: Duration): Promise[Unit] = {
          val promise = Promise[Unit]()
          Future {
            Thread.sleep(duration.toMillis)
            promise.success(())
          }
          promise
      }
      val psOutput: Sink[String, Future[Done]] = ...
      println("Equivalent of `kubectl exec ps aux`")
      k8s.exec(podName = podName, command = Seq("ps", "aux"), maybeStdIn = None, maybeStdout = Some(psOutput), maybeClose = Some(closeAfter(5.seconds)))
```
Directly scale up and down the number of replicas of a deployment or stateful set:
```scala
val scaledDeploymentFut = for {
  del <- k8s.get[Deployment]("example")
  _ = println("Now scale deployment down to 1 replica")
  currentScale <- k8s.getScale[Deployment](nginxDeployment.name)
  downScale = currentScale.withSpecReplicas(1)
  scaledDown <- k8s.updateScale[Deployment](nginxDeployment.name, downScale)
  _ = println("Scale desired = " + scaledDown.spec.replicas + ", current = " + scaledDown.status.get.replicas)
  _ = println("Now directly scale up to 4 replicas")
  upScale = scaledDown.withSpecReplicas(4)
  scaledUp <- k8s.updateScale[Deployment](nginxDeployment.name, upScale)
  _ = println("Scale object returned: specified = " + scaledUp.spec.replicas + ", current = " + scaledUp.status.get.replicas)
} yield scaledUp
```

### Error Handling

Any call to the Skuber API methods that results in a non-OK status response from Kubernetes will cause the result of the Future returned by the method to be set to a `Failure` with an exception of class `K8SException`. This exception has a `status` field of type `Status` that encapsulates details of the error if these details are returned by Kubernetes, which is usually the case for a non-OK status code.

The `Status` class is defined as follows:
```scala
case class Status(
  // ... metadata fields here ...
  // error details below:
  status: Option[String] = None,
  message: Option[String] = None,
  reason: Option[String] = None,
  details: Option[Any] = None,
  code: Option[Int] = None  // HTTP status code
) 
```

How these exceptions should be handled is of course highly context-dependent. For example, sometimes you may want to ignore a NOT_FOUND (404) error when attempting to delete an object, because it is normal and ok if it has already been deleted:
```scala
val deleteResult = k8s.delete[Deployment](c.name).recover { 
  case ex: K8SException if (ex.status.code.contains(404)) => // ok - no action required 
}
deleteResult.onComplete {
  case Success(result) => ...
  case Failure(ex) => 
    log.error("Error when deleting " + c.name + ", reason: " + ex.status.reason.getOrElse("<unknown>"))
} 	
```
The above code basically causes a 404 error to be silently ignored and the overall result will be a `Success`, other errors will still be propagated as `Failures` in `deleteResult`, which results in the error reason being logged.

### Reactive Watch API

Kubernetes supports the ability for API clients to watch events on specified resources - as changes occur to the resource(s) on the cluster, Kubernetes sends details of the updates to the watching client.
Skuber v3 now uses Pekko or Akka streams to consume the events.
In SKuber V3, the first step to streaming events is to create a `Watcher` on the kind of resource you are interested in:

`k8s.getWatcher[<kind>]`

Various watch operations can then be called on this to stream desired events. For example:

```scala
import skuber.json.format._
import skuber.api.client.EventType
import skuber.model.apps.v1.Deployment
   
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl._
    
object WatchExamples {
  // create an nginx deployment then watch all changes to it
  val deployment: Deployment = buildNginxDeployment("nginx')
  k8s.create(deployment).map { d =>
    Thread.sleep(10000) // for demo purposes wait for deployment to complete startup before watching it
    k8s.list[Deployment]().map { l =>
      k8s.getWatcher[Deployment].watchObjectStartingFromVersion(deploymentName, l.resourceVersion)
        .viaMat(KillSwitches.single)(Keep.right)
        .filter(event => event._object.name == deploymentName)
        .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
        .wireTap { event =>
          System.out.println(event._object.status.get.updatedReplicas)
        }
        .toMat(Sink.collection)(Keep.both)
        .run()
      }
    }  
  }
}
```

The above example creates a Watch on the frontend deployment, and feeds the resulting events into an Akka sink that simply prints out the replica count as the deployment gets updated.

You can demonstrate this by running the above code while also running some  `kubectl` commands:
```bash
kubectl scale --replicas=1 deployment/frontend
kubectl scale --replicas=10 deployment/frontend
kubectl scale --replicas=0 deployment/frontend
```

You should see updated replica counts being printed out by the sink as the scaling progresses.

You'll notice that the above example watches future events only by first getting the current version of the resource collection using `list` and then starting a watch from that version.
A simpler alternative is to not set a resource version on the watch request, in this case using the `watchObject` instead of `watchObjectStartingWithVersion` - this starts watching from the most recent version.

```scala
  // ... 

  // create an nginx deployment then watch all changes to it
  val deployment: Deployment = buildNginxDeployment("nginx')
  k8s.create(deployment).map { d =>
      Thread.sleep(10000)
    // this should start watching this deployment from most recent version
    k8s.getWatcher[Deployment].watchObject(deploymentName)
      .viaMat(KillSwitches.single)(Keep.right)
  
  // ...
```

See [Kubernetes watch semantics](https://kubernetes.io/docs/reference/using-api/api-concepts/#semantics-for-watch) for details on how the optional resource version setting impacts watch semantics.

The above watches a single object - alternatively you can watch events on all resources (at namespace or cluster scope) of a given kind  - for example the following watches all pod phase changes in the default namespace
```scala
  // ...
   
  val podPhaseMonitor = Sink.foreach[WatchEvent[Pod]] { podEvent =>
    val pod = podEvent._object
    val phase = pod.status.flatMap { _.phase }
    println(podEvent._type + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>"))
  }

  k8s.getWatcher[Pod].watch() // watch all pods in current namespace from most recent version
  
  // ...
}
```

The same as above but for pods in all namespaces (cluster scope):

```scala
  // ...
   
  val podPhaseMonitor = Sink.foreach[WatchEvent[Pod]] { podEvent =>
    val pod = podEvent._object
    val phase = pod.status.flatMap { _.phase }
    println(podEvent._type + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>"))
  }

  val allPodsWatch = k8s.getWatcher[Pod].watchCluster() // watch all pods in cluster from most recent version
  allPodsWatch.runWith(podPhaseMonitor)
}
```

For each watch method such as the above that leaves the resource version unset, there is an alternative method that enables the resource version to be specified so that you can watch from a specific version (or just set it to "0" to watch from any version)
See [the Watcher trait](../core/src/main/scala/skuber/api/client/Watcher.scala) for all supported methods.

### Building Resources

Skuber supports a fluent API approach to building resources of all kinds using its model.

This is best demonstrated by some examples.

***Deployment***

This example builds an Nginx deployment using the Skuber fluent API, then creates the equivalent resource on the cluster.
```scala
import skuber.model.{Container, Pod}
import skuber.model.apps.v1.Deployment

val defaultNginxVersion = "1.29.1"
val defaultNginxPodName = "nginx"
val defaultNginxContainerName = "nginx"

// The following constructs an nginx container 
def buildNginxContainer(version: String, containerName: String = defaultNginxContainerName): Container = Container(name =  containerName, image = "nginx:" + version).exposePort(80)

// The following constructs an nginx deployment ith two replicas
def buildNginxDeployment(deploymentName: String, version: String = defaultNginxVersion): Deployment = {
  val nginxContainer = getNginxContainer(version)
  val nginxTemplate = Pod.Template.Spec.named("nginx").addContainer(nginxContainer).addLabel("app" -> "nginx")
  Deployment(deploymentName).withTemplate(nginxTemplate).withReplicas(2).withLabelSelector("app" is "nginx")
}

...
val deplFuture = k8s.create(buildNginxDeployment("nginx)")) // create the deployment on the cluster
```

Use `kubectl get deployments` to see the status of the newly created Deployment.

Later an update can be posted - in this example the nginx version will be updated to 1.29.2, and the deployment controller will ensure it is updated using the specified rolling update strategy.
```scala
val newContainer = Container("nginx",image="nginx:1.29.2").exposePort(80)
val existingDeployment = k8s.get[Deployment]("nginx")
val updatedDeployment = existingDeployment
    .updateContainer(newContainer)
    .withStrategy(Strategy(RollingUpdate(maxSurge = Left(2), maxUnavailable = Left(1))))
k8s.update(updatedDeployment)
```

***HorizontalPodAutoscaler***

A skuber client can also manage `HorizontalPodAutoscaler` objects in order to autoscale a replication controller or deployment. A fluent API approach enables minimum replica count, maximum replica count and CPU utilisation target to be readily specified. For example:
```scala
import skuber.model.autoscaling.v1._

// following autoscales 'controller' with min replicas of 2, max replicas of 8 
// and a target CPU utilisation of 80%
val hpas = HorizontalPodAutoscaler.scale(controller)
  .withMinReplicas(2)
  .withMaxReplicas(8)
  .withCPUTargetUtilization(80)
k8s.create[HorizontalPodAutoscaler](hpas)
```

***Ingress***
 
An ingress controller manages handling of HTTP requests from an ingress point on the Kubernetes cluster, proxying then to target services according to a user-specified set of routing rules. The rules are specified in a standard format, although different ingress controllers can utilize different underlying mechanisms to control ingress (e.g. an nginx proxy, or by configuring a hardware or cloud load balancer).

The `NginxIngress` example illustrates creation and testing of an ingress, using an nginx-based ingress controller from the Kubernetes contrib project.

### Other API groups

Aside from the `core` and `apps` groups, there are multiple other groups. These are some of the other packages in the Skuber model, each mapping to a different Kubernetes API group:

***batch***

Contains the `Job` and `CronJob` kinds. One of the skuber examples demonstrates a client of the `Job` type.

***rbac***

Contains the `Role`,`RoleBinding`,`ClusterRole` and `ClusterRoleBinding` kinds - see the Kubernetes Role-Based Access Control documentation for details on how to use these kinds.

***apiextensions.v1***

Currently supports one kind - the `CustomResourceDefinition` kind introduced in Kubernetes V1.7

***networking***

Supports `NetworkPolicy` resources (for Kubernetes v1.7 and above) - see Kubernetes [Network Policies](https://kubernetes.io/docs/concepts/services-networking/network-policies/) documentation.

## Custom Resources

[Custom Resources](https://kubernetes.io/docs/concepts/api-extension/custom-resources/) are a powerful feature which enables Kubernetes clients to define and use their own custom resources in the same way as built-in resource kinds. 

They are useful for building Kubernetes operators and other advanced use cases. 

Skuber offers a means for applications to define their own custom resources (_Custom Resource Definitions_, or CRDs) as Scala classes, and then use them with the API in just the same way as built-in resource object and list types.

### Defining a custom resource

To use custom resources in Skuber, you will need to define the model for the "payload" of the custom resource, which following standard Kubernetes conventions will contain a single `spec` section and a single (optional) `status` section.

Lets work through a simple example, which represents some custom autoscaler kind on which you could build an operator that can set a desired number of replicas in the spec and access the current actual replica count in the status:

```scala
object CustomAutoscaler {
  case class Spec(desiredReplicas: Int)
  case class Status(actualReplicas: Int)
}
```
The Skuber API will also need to be able to read and write the spec and status, for which implicit Play formatters must be defined

```scala
object CustomAutoscaler {
  // ..
  
  import play.api.libs.json._
  implicit val specFmt: Format[Spec] = Json.format[Spec]
  implicit val statusFmt: Format[Status] = Json.format[Status]
}
```

You can now define a CRD containing these spec and status fields by specifying a`CustomReosurce` type as follows:

```scala
object CustomAutoscaler {
  // ..

  import skuber.model.{CustomResource, ListResource, ResourceDefinition}

  type CustomAutoscaler = CustomResource[Spec, Status] // this is the resource object type that will be passed to the Skuber API
  type CustomAutoscalerList = ListResource[CustomAutoscaler] // this is the equivalent resource list type for use with the API

  implicit val asResourceDefinition: ResourceDefinition[CustomAutoscaler] = ResourceDefinition[CustomAutoscaler](
    group = "autoscaler.example.skuber.io",
    version = "v1alpha1",
    kind = "CustomAutoscaler"
  )

  // Convenience method for constructing custom resources of the required type from a name snd a spec
  def apply(name: String, spec: Spec): CustomAutoscaler = CustomResource[Spec, Status](spec).withName(name)
}
````

The above definitions provides the key type information needed by the Kubernetes API server, and should match the CRD on the cluster (which this example assumes is defined outside of Skuber)

Now we are ready to manipulate custom resources of `CustomAutoscaler` type, for example:

```scala
import CustomAutoscaler._

// create a new autoscaler resource on the cluster
k8s.create(CustomAutoscaler("myCustomAutoscaler", Spec(desiredReplicas=4)))

// retrieve the custom resource we just created
val myAsFuture = k8s.get[CustomAutoscaler]("myCustomAutoscaler")

// list all resources of this kind
k8s.list[CustomAutoscalerList].map { l =>
    l.items.foreach { as =>
        System.out.println(s"Actual replicas = ${as.status.map(_.actualReplicas)}")
    }
}

// watch all resources of this type in the current namespace
val asEventsSource: Source[WatchEvent[CustomAutoscaler], _] = k8s.getWatcher[CustomAutoscaler].watch()
```

The ability to easily watch custom resources is crucial to implementing advanced use cases in Skuber, especially operators.

The above demonstrates the most common use case of managing custom resources on the cluster, as opposed to managing the associated definitions (CRDs). In the less likely use case where you are using Skuber to manage the lifecycle of CRDs on the cluster, additional definitions will be needed. The integration tests include a well-documented and comprehensive [test](../integration/src/test/scala/skuber/CustomResourceSpec.scala) that shows how to implement this as well as other advanced custom resource operations such as Scale subresource usage.

## Label Selectors

Skuber supports a mini-DSL to build label selectors, which can be used by `list` and `watch` operations to select resources based on the labels applied to them:

Label selectors are also embedded in certain workload types like deployments:
```scala
import skuber.model.LabelSelector
import LabelSelector.dsl._
import skuber.model.apps.v1.Deployment 

val sel = LabelSelector(
  "tier" is "frontend",
  "release" doesNotExist,
  "env" isNotIn List("production", "staging")
)

val depl = Deployment("exampleDeployment").withSelector(sel)

// now use the selector with pod list/watch operations
import skuber.api.client.WatchParameters
import skuber.model.{Pod, PodList]
import skuber.json.format._

// List all pods matching the selector
val frontendPodsFuture = k8s.listSelected[PodList](sel)
  
// Watch all pods matching the selector
val frontEndWatchSelector = WatchParameters(labelSelector = Some(sel))
val frontEndPodEventSource = k8s.getWatcher[Pod].watchWithParameters(frontEndWatchSelector)

```

## Programmatic configuration

Normally it is likely that configuration will be via a kubeconfig file. However a client can optionally pass a `K8SConfiguration` object directly as a parameter to the `k8sInit` call. This will override any other configuration. 

The configuration object has the same information as a kubeconfig file - in fact, the kubeconfig file is deserialised into a K8SConfiguration object. 

The unit tests have an example of a K8SConfiguration object being parsed from an input stream that contains the data in kubeconfig file format.

Additionally a Typesafe Config object can optionally be passed programmatically as a second parameter to the initialisation call - currently this only supports specifying your own Pekko/Akka (as appropriate) dispatcher (execution context for the http client request processing by Skuber) 
