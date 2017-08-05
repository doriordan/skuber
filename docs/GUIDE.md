# Skuber Programming Guide

This guide assumes a working knowledge of Kubernetes concepts.

## Data Model Overview

The Skuber data model is a representation of the Kubernetes types / kinds in Scala.

The Skuber data model for the the original core Kubernetes API group (which manages many of the most fundamental Kubernetes kinds) is defined in the top-level package, so they can be easily imported into your application:

    import skuber._
    
This also imports many other common types and aliases that are generally useful.

Example of more specific core API kind imports:
   
    import skuber.{Service,ServiceList,ReplicationController}   

Newer (non-core) API group classes are contained in subpackages associated with each API group. For example`skuber.ext` for the extensions API group or `skuber.rbac` for the rbac API group. Example specific imports for these kinds:

    import skuber.ext.{DaemonSet,Ingress}
    import skuber.batch.{Job,CronJob}

The model can be divided into categories which correspond to those in the Kubernetes API:

- [Object kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#objects): These represent persistent entities in Kubernetes. All object kinds are mapped to case classes that extend the `ObjectResource` abstract class. The `ObjectResource` class defines the common fields, notably `metadata` (such as name, namespace, uid, labels etc.). The concrete classes extending ObjectResource typically define [spec and status](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#spec-and-status) nested fields whose classes are defined in the companion object (e.g. `Pod.Spec`, `ReplicationController.Status`).
Object kind classes include `Namespace`, `Pod`,`Node`, `Service`, `Endpoints`, `Event`, `ReplicationController`, `PersistentVolume`, `PersistentVolumeClaim`, `ServiceAccount`, `LimitRange`, `Resource.Quota`, `Secret`,`Deployment`,`HorizontalPodAutoScaler`,and `Ingress` amongst others.  

- [List kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#lists-and-simple-kinds): These represent lists of object resources, and in skuber are typically returned by one of the `list` API methods. All list kinds are mapped to a `ListResource[O]` case class supporting access to basic metadata and the object kind specific items in the list. 
There are thus list kinds for each object kind e.g. `ListResource[Pod]`,`ListResource[Node]`, and skuber also defines type aliases defined for each supported list kind e.g. `PodList`,`NodeList`.   

- [Simple kinds](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#lists-and-simple-kinds) 

### Fluent API

A combination of generic Scala case class features and Skuber-defined fluent API methods make building out even relatively complex specifications for creation or modification on Kubernetes straightforward. The following (which can be found under the examples project) illustrates just a small part of the API:

    val prodLabel = "env" -> "production"
    val prodInternalZoneLabel = "zone" -> "prod-internal"

    val prodInternalSelector = Map(prodLabel, prodInternalZoneLabel)

    val prodCPU = 1 // 1 KCU 
    val prodMem = "0.5Gi" // 0.5GiB (gibibytes)    

    val prodContainer=Container(name="nginx-prod", image="nginx").
                          limitCPU(prodCPU).
                          limitMemory(prodMem).
                          port(80)
    
    val internalProdPodSpec=Pod.Spec(containers=List(prodContainer), 
                                     nodeSelector=Map(prodInternalZoneLabel))     

    val internalProdController=ReplicationController("nginx-prod-int").
                                  addLabels(prodInternalSelector).
                                  withSelector(prodInternalSelector).
                                  withReplicas(8).
                                  withPodSpec(internalProdPodSpec)
             
The unit tests in the skuber subproject contains more examples, along with the examples subproject itself.

## JSON Mapping

Kubernetes defines specific JSON representations of its types. Skuber implements Play JSON read/write [converters](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators) for mapping between the model classes and their JSON representations. These implicit converters (formatters) can be made available to your application via import statements, for example, to import all formatters for the core API group:
 
    import skuber.json.format._

Similiarly, subpackages of `skuber.json` contain formatters for non-core API group kinds such as `Deployment` etc.

There are many available examples of JSON representations of Kubernetes objects, for example [this file](https://github.com/kubernetes/kubernetes/blob/master/examples/guestbook-go/guestbook-controller.json) specifies a replication controller for the main Kubernetes project Guestbook example. To convert that JSON representation into a Skuber `ReplicationController` object:

    import skuber.json.format._
    import skuber.ReplicationController

    import play.api.libs.json.Json   
    import scala.io.Source

    val controllerURL = "https://raw.githubusercontent.com/kubernetes/kubernetes/master/examples/guestbook-go/guestbook-controller.json"
    val controllerJson = Source.fromURL(controllerURL).mkString 
    val controller = Json.parse(controllerJson).as[ReplicationController]
    println("Name: " + controller.name)
    println("Replicas: " + controller.replicas)

Equally it is straightforward to do the reverse and generate a JSON value from a Skuber model object:

    val json = Json.toJson(controller)

## API

### The API basics

These are the basic steps to use the Skuber API:

- Import the API definitions from the appropriate package(s)
- Import the implicit JSON formatters from the appropriate package(s). The API uses these to read/write the request and response data.
- Ensure a Scala implicit `ExecutionContext` is available - this will be the execution context in which the `Future`s created for each request will execute.
- Create a request context by calling `k8sInit` - this establishes the connection and namespace details for requests to the API
- Invoke the required requests using the context.
- The requests generally return their results (usually object or list kinds) asynchronously via `Future`s.

For example, the following creates the Replication Controller we just parsed above on our Kubernetes cluster:

    import skuber._
    import skuber.json.format._
    import scala.concurrent.ExecutionContext.Implicits.global
    val k8s = k8sInit
  
    k8s create controller

When finished making requests the application should call `close` on the request context to release the underlying connection-related resources.
     
### API Method Summary

Create a resource on Kubernetes from a Skuber object kind:

    val rcFut = k8s create controller
    rcFut onSuccess { case rc => 
      println("Created controller, Kubernetes assigned resource version is " rc.metadata.resourceVersion) 
    }

Get a Kubernetes object kind resource by type and name: 

    val rcFut = k8s get[ReplicationController] "guestbook"
    rcFut onSuccess { case rc => println("Current replica count = " + rc.status.get.replicas) }

Get a list of all Kubernetes objects of a given list kind in the current namespace:

    val rcListFut = k8s list[ReplicationControllerList]()
    rcListFut onSuccess { case rcList => rcList foreach { rc => println(rc.name) } }
    
As above, but for a specified namespace:

    val ksysPods: Future[PodList] = k8s listInNamespace[PodList]("kube-system")
    
Get lists of all Kubernetes objects of a given list kind for all namespaces in the cluster, mapped by namespace:
    
    val allPodsMapFut: Future[Map[String, PodList]] = k8s listByNamespace[PodList]()

(See the ListExamples example for examples of the above list operations)
    
Update a Kubernetes object kind resource:

    val upscaledController = controller.withReplicas(5)
    val rcFut = k8s update upscaledController
    rcFut onSuccess { case rc => 
      println("Updated controller, Kubernetes assigned resource version is " + rc.metadata.resourceVersion) 
    }

Delete a Kubernetes object:

    val rmFut = k8s delete[ReplicationController] "guestbook"
    rmFut onSuccess { case _ => println("Controller removed") }

Note: There is no support yet for the Kubernetes API [PATCH operations](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#patch-operations)

### Error Handling

Any call to the Skuber API methods that results in a non-OK status response from Kubernetes will cause the result of the Future returned by the method to be set to a `Failure` with an exception of class `K8SException`. This exception has a `status` field that encapsulates the data returned in the [Status](https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#response-status-kind) object if Kubernetes has returned one, which it generally does when returning a non-OK status.

This exception can be handled in the appropriate manner for your use case by using the standard Scala Future failure handling mechanisms. For example, sometimes you may want to ignore a NOT_FOUND (404) error when attempting to delete an object, because it is normal and ok if it has already been deleted:

    val deleteResult = (k8s delete[ReplicationController] c.name) recover { 
      case ex: K8SException if (ex.status.code.contains(404)) => // ok - no action required 
    }
    deleteResult onFailure {
      case ex: K8SException => 
        log.error("Error when deleting " + c.name + ", reason: " + ex.status.reason.getOrElse("<unknown>"))
    } 	

The above code basically causes a 404 error to be silently ignored and the overall result will be a `Success`, other errors will still be propagated as `Failures` in `deleteResult`, which results in the error reason being logged.

The `Status` class is defined as follows:

    case class Status(
      // ... metadata fields here ...
      // error details below:
      status: Option[String] = None,
      message: Option[String] = None,
      reason: Option[String] = None,
      details: Option[Any] = None,
      code: Option[Int] = None  // HTTP status code
    ) 

### Reactive Watch API

Kubernetes supports the ability for API clients to watch events on specified resources - as changes occur to the resource(s) on the cluster, Kubernetes sends details of the updates to the watching client.  Skuber supports an [Iteratee](https://www.playframework.com/documentation/2.4.x/Iteratees) API for handling these events reactively, providing a `watch` method that returns an `Enumerator` of events which can then be fed to your Iteratee. The following example can be found in the examples sub-project:

    import skuber._
    import skuber.json.format._
  
    import scala.concurrent.ExecutionContext.Implicits.global

    import play.api.libs.iteratee.Iteratee

    object WatchExamples {
      def watchFrontendScaling = {
        val k8s = k8sInit    
        val frontendFetch = k8s get[ReplicationController] "frontend"
        frontendFetch onSuccess { case frontend =>
          val frontendWatch = k8s watch frontend
          frontendWatch.events |>>> Iteratee.foreach { frontendEvent => 
            println("Current frontend replicas: " + frontendEvent._object.status.get.replicas) 
          }
        }     
      }
      // ...
    }

To test the above code, call the watchFrontendScaling method to create the watch and then separately run a number of [kubectl scale](https://kubernetes.io/docs/tutorials/kubernetes-basics/scale-interactive/) commands to set different replica counts on the frontend - for example:

     kubectl scale --replicas=1 rc frontend
     
     kubectl scale --replicas=10 rc frontend

     kubectl scale --replicas=0 rc frontend

You should see updated statuses being printed out by the Iteratee as the scaling progresses.

The [reactive guestbook](../examples/src/main/scala/skuber/examples/guestbook) example also uses the watch API to support monitoring the progress of deployment steps by watching the status of replica counts.

Additionally you can watch all events related to a specific kind - for example the following can be found in the same example:

    def watchPodPhases = {
      val k8s = k8sInit    

      // watch only current events - i.e. exclude historic ones  - 
      // by specifying the resource version returned with the latest pod list     

      val currPodList = k8s list[PodList]()
     
      currPodList onSuccess { case pods =>
        val latestPodVersion = pods.metadata.map { _.resourceVersion } 
        val podWatch = k8s watchAll[Pod](sinceResourceVersion=latestPodVersion) 
       
        podWatch.events |>>> Iteratee.foreach { podEvent => 
          val pod = podEvent._object
          val phase = pod.status flatMap { _.phase }
          println(podEvent._type + " => Pod '" + pod.name + "' .. phase = " + phase.getOrElse("<None>"))    
        } 
      }
      // ...
    }

The watch can be demonstrated by calling `watchPodPhases` to start watching all pods, then in the background run the reactive guestbook example: you should see events being reported as guestbook pods are deleted, created and modified during the run.

Note that both of the examples above watch only those events which have a later resource version than the latest applicable when the watch was created - this ensures that only current events are sent to the watch, historic ones are ignored - this is probably what you want. 

### Extensions API Group

Along with the core API group, the extensions API group is probably the most commonly used as it traditionally contains some key types.

For example, to use the `HorizontalPodAutoscaler` kind:

    import skuber.ext.HorizontalPodAutoscaler
    import skuber.json.ext.format._ // imports the implicit JSON formatters required to use extensions group resources
    
The currently supported extensions group kinds include `ReplicaSet`,`HorizaontalPodAutoscaler`, `Ingress` and `DaemonSet`, together with their list kinds. 
(Note that `Deployment` has been moved from this package to the `apps` package, reflecting a corresponding change in Kubernetes. There is a type alias to support backwards compatibility, but usage of `skuber.ext.Deployment` is deprecated in favour of `skuber.apps.Deployment`. As the Kubernetes long-term strategy is to use more specific API groups rather then the generic extensions group, other kinds in this subpackage may also be moved in future to reflect changes in Kubernetes.)

***HorizontalPodAutoscaler***

A skuber client can also manage `HorizontalPodAutoscaler` objects in order to autoscale a replication controller or deployment. A fluent API approach enables minimum replica count, maximum replica count and CPU utilisation target to be readily specified. For example:

    // following autoscales 'controller' with min replicas of 2, max replicas of 8 
    // and a target CPU utilisation of 80%
    val hpas = HorizontalPodAutoscaler.scale(controller).
                     withMinReplicas(2).
                     withMaxReplicas(8).
                     withCPUTargetUtilization(80)
    k8s create[HorizontalPodAutoscaler] hpas  

The other standard Skuber API methods (`update`, `delete` etc.) can also be used with this type. (Note: the corresponding *list type* will be supported shortly)

***Ingress***
 
An ingress controller manages handling of HTTP requests from an ingress point on the Kubernetes cluster, proxying then to target services according to a user-specified set of routing rules. The rules are specified in a standard format, although different ingress controllers can utilize different underlying mechanisms to control ingress (e.g. an nginx proxy, or by configuring a hardware or cloud load balancer).

The `NginxIngress` example illustrates creation and testing of an ingress, using an nginx-based ingress controller from the Kubenretes contrib project.

***ReplicaSet***

ReplicaSet is the expected long-term successor of ReplicationController in the Kubernetes project. It is currently different only in supporting both equality and set based label selectors (ReplicationController only support equality-based ones).

The `NginxIngress` example uses a ReplicaSet to manage the ingress controller.

### Other API groups

Aside from the `core` and `extensions` groups, more recent Kubernetes kinds tend to be defined in other, more targetted API groups. Currently skuber supports the following subpackages, each mapping to a different Kubernetes API group:

***apps***

Currently contains the `Deployment` and `StatefulSet` types:

- Deployment

(Note that a recent version of Kubernetes is needed to use the Deployment functionality in this release of Skuber).

A Skuber client can create and update `Deployment` objects on the cluster to have Kubernetes automatically manage the deployment and upgrade strategy (for example rolling upgrade) of applications to the cluster.

The following example emulates that described [here](http://kubernetes.io/docs/user-guide/deployments/). 

Initial creation of the deployment:

    val nginxLabel = "app" -> "nginx"
    val nginxContainer = Container("nginx",image="nginx:1.7.9").port(80)
    
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

Use `kubectl get deployments` to see the status of the newly created Deployment, and `kubectl get rc` will show a new replication controller which manages the creation of the required pods.

Later an update can be posted - in this example the nginx version will be updated to 1.9.1:

    val newContainer = Container("nginx",image="nginx:1.9.1").port(80)
    val existingDeployment = k8s get[Deployment] "nginx-deployment"
    val updatedDeployment = existingDeployment.updateContainer(newContainer)
    k8s update updatedDeployment 

As no explicit deployment strategy has been selected, the default strategy will be used which will result in a rolling update of the nginx pods - again, you can use `kubectl get` commands to view the status of the deployment, replication controllers and pods as the update progresses.

The `DeploymentExamples` example runs the above steps.

- StatefulSet

This can be used to manage stateful applications on Kubernetes - such as databases - analagous to the way in which `ReplicaSet` is used to manage stateless applications.

***batch***

Contains the `Job` and `CronJob` kinds. One of the skuber examples demonstrates a client of the `Job` type.

***rbac***

Contains the `Role`,`RoleBinding`,`ClusterRole` and `ClusterRoleBinding` kinds - see the Kubernetes Role-Based Access Control documentation for details on how to use these kinds.

***apiextensions***

Currently supports one kind - the `CustomResourceDefinition` kind introduced in Kubernetes V1.7 (as successor to the now deprecated `Third Party Resources` kind, which is not supported in Skuber). 

[Custom Resources](https://kubernetes.io/docs/concepts/api-extension/custom-resources/) are a powerful feature which enable Kubernetes clients to define and use their own custom resources to be treated in the same way as built-in kinds. They are useful for building custom controllers and other advanced use cases. See the `customresources` example in the `examples` sub-project to see how you can define custom resources that are first-class citizens in the Skuber API.


## Label Selectors

As alluded to above, newer API types such as ReplicaSets and Deployments support set-based as well as equality-based [label selectors](http://kubernetes.io/docs/user-guide/labels/#label-selectors). 
For such types, Skuber now supports a mini-DSL to build selectors:

	import skuber._
	import skuber.LabelSelector.dsl._

    val sel = LabelSelector(
        "tier" is "frontend",
        "release" doesNotExist,
        "env" isNotIn List("production", "staging"))
        
    // now the label selector can be used with certain types 
    val depl = Deployment("exampleDeployment").withSelector(sel)
       


 
## Programmatic configuration

Normally it is likely that configuration will be via a kubeconfig file. However a client can optionally pass a `K8SConfiguration` object directly as a parameter to the `k8sInit` call. This will override any other configuration. 

The configuration object has the same information as a kubeconfig file - in fact, the kubeconfig file is deserialised into a K8SConfiguration object. 

The unit tests have an example of a K8SConfiguration object being parsed from an input stream that contains the data in kubeconfig file format.
