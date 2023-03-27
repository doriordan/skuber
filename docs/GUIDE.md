# Skuber Programming Guide

This guide assumes a working knowledge of Kubernetes concepts.

## Data Model Overview

The Skuber data model is a representation of the Kubernetes types / kinds in Scala.

The Skuber data model for the the original core Kubernetes API group (which manages many of the most fundamental Kubernetes kinds) is defined in the top-level package, so they can be easily imported into your application:

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

### Fluent API

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

Kubernetes defines specific JSON representations of its types. Skuber implements Play JSON read/write [converters](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators) for mapping between the model classes and their JSON representations. These implicit converters (formatters) can be made available to your application via import statements, for example, to import all formatters for the core API group:

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
## API

### The API basics

These are the basic steps to use the Skuber API:

- Import the API definitions from the appropriate package(s)
- Import the implicit JSON formatters from the appropriate package(s) as described above. The API uses these to read/write the request and response data.
- Declare some additional Pekko implicit values as shown below (this is basically to configure the Pekko HTTP client which Skuber v2 uses under the hood)
- Create a Kubernetes client by calling `k8sInit` - this establishes the connection and namespace details for requests to the API
- Invoke the required requests using the client
- The requests generally return their results (usually object or list kinds) asynchronously via `Future`s.

For example, the following creates a pod  on our Kubernetes cluster:
```scala
import skuber._
import skuber.json.format._
    
import org.apache.pekko.actor.ActorSystem
implicit val system = ActorSystem()
implicit val dispatcher = system.dispatcher
    
val k8s = k8sInit
val pod: Pod = ??? // read a Pod definition from some file or other source 
k8s create pod 
```

When finished making requests the application should call `close` on the Kubernetes client. Note that this call no longer closes connection resources since Skuber migrated to using Akka, because the use of application-supplied  implicit Akka actor systems means Skuber cannot be sure that other application components are not also using the same actor system. Therefore the application should explicitly perform any required Akka cleanup, e.g.

```scala  
k8s.close
system.terminate
```     
### API Method Summary

(See [here](https://github.com/doriordan/skuber/blob/master/client/src/main/scala/skuber/api/client/KubernetesClient.scala) for the latest complete API documentation)

Create a resource on Kubernetes from a Skuber object kind:
```scala
val rcFut = k8s create controller
rcFut onSuccess { case rc => 
  println("Created controller, Kubernetes assigned resource version is " rc.metadata.resourceVersion) 
}
```

Get a Kubernetes object kind resource by type and name: 
```scala
val depFut = k8s get[Deployment] "guestbook"
depFut onSuccess { case dep => println("Current replica count = " + dep.status.get.replicas) }
```

Get a list of all Kubernetes objects of a given list kind in the current namespace:
```scala
val depListFut = k8s list[DeploymentList]()
depListFut onSuccess { case depList => depList foreach { dep => println(dep.name) } }
```
    
As above, but for a specified namespace:
```scala
val ksysPods: Future[PodList] = k8s listInNamespace[PodList]("kube-system")
```
    
Get lists of all Kubernetes objects of a given list kind for all namespaces in the cluster, mapped by namespace:
```scala    
val allPodsMapFut: Future[Map[String, PodList]] = k8s listByNamespace[PodList]()
```
(See the ListExamples example for examples of the above list operations)
    
Update a Kubernetes object kind resource:
```scala
val upscaledDeployment = deployment.withReplicas(5)
val depFut = k8s update upscaledDeployment
depFut onSuccess { case dep => 
  println("Updated deployment, Kubernetes assigned resource version is " + dep.metadata.resourceVersion) 
}
```

Delete a Kubernetes object:
```scala
val rmFut = k8s delete[Deployment] "guestbook"
rmFut onSuccess { case _ => println("Deployment removed") }
```
(There is also a `deleteWithOptions` call that enables options such as propagation policy to be passed with a Delete operation.)

Patch a Kubernetes object using a [JSON merge patch](https://tools.ietf.org/html/rfc7386):
```scala   
val patchStr="""{ "spec": { "replicas" : 1 } }""" 
val stsFut = k8s.jsonMergePatch(myStatefulSet, patchStr)
```
See also the `PatchExamples` example. Note: There is no patch support yet for the other two (`json patch` and `strategic merge patch`) [strategies](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#patch-operations)

Get the logs of a pod (as an Akka Streams Source):
```scala
    val helloWorldLogsSource: Future[Source[ByteString, _]]  = k8s.getPodLogSource("hello-world-pod", Pod.LogQueryParams())
```

Directly scale the number of replicas of a deployment or stateful set:
```scala
k8s.scale[StatefulSet]("database", 5)
```

### Error Handling

Any call to the Skuber API methods that results in a non-OK status response from Kubernetes will cause the result of the Future returned by the method to be set to a `Failure` with an exception of class `K8SException`. This exception has a `status` field that encapsulates the data returned in the [Status](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#response-status-kind) object if Kubernetes has returned one, which it generally does when returning a non-OK status.

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

### Reactive Watch API

Kubernetes supports the ability for API clients to watch events on specified resources - as changes occur to the resource(s) on the cluster, Kubernetes sends details of the updates to the watching client. Skuber v2 now uses Akka streams for this (instead of Play iteratees as used in the Skuber v1.x releases), so the `watch[O]` API calls return `Future[Source[O]]` objects which can then be plugged into Akka flows.
```scala
import skuber._
import skuber.json.format._
import skuber.apps.v1.Deployment
   
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
    
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

The [reactive guestbook](../examples/src/main/scala/skuber/examples/guestbook) example also uses the watch API to support monitoring the progress of deployment steps by watching the status of replica counts.

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
```scala
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
 
## Programmatic configuration

Normally it is likely that configuration will be via a kubeconfig file. However a client can optionally pass a `K8SConfiguration` object directly as a parameter to the `k8sInit` call. This will override any other configuration. 

The configuration object has the same information as a kubeconfig file - in fact, the kubeconfig file is deserialised into a K8SConfiguration object. 

The unit tests have an example of a K8SConfiguration object being parsed from an input stream that contains the data in kubeconfig file format.

Additionally a Typesafe Config object can optionally be passed programmatically as a second parameter to the initialisation call - currently this only supports specifying your own Akka dispatcher (execution context for the Akka http client request processing by Skuber) 
