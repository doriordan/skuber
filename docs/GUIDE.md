# Skuber Programming Guide

This guide assumes a working knowledge of Kubernetes concepts.

## Data Model Overview

The Skuber data model is a representation of the Kubernetes types / kinds in Scala.

The entire Skuber data model can be easily imported into your application:

    import skuber._
 
The model can be divided into categores which correspond to those in the Kubernetes API:

- [Object kinds](http://kubernetes.io/v1.0/docs/devel/api-conventions.html#objects): These represent persistent entities in Kubernetes. All object kinds are mapped to case classes that extend the `ObjectResource` abstract class. The `ObjectResource` class defines the common fields, notably `metadata` (such as name, namespace, uid, labels etc.). The concrete classes extending ObjectResource typically define [spec and status](http://kubernetes.io/v1.0/docs/devel/api-conventions.html#spec-and-status) nested fields whose classes are defined in the companion object (e.g. `Pod.Spec`, `ReplicationController.Status`).
Object kind classes include `Namespace`, `Pod`,`Node`, `Service`, `Endpoints`, `Event`, `ReplicationController`, `PersistentVolume`, `PersistentVolumeClaim`, `ServiceAccount`, `LimitRange`, `Secret`.   

- [List kinds](http://kubernetes.io/v1.0/docs/devel/api-conventions.html#lists-and-simple-kinds): These represent lists of other kinds. All list kinds are mapped to classes implementing a `KList` trait supporting access to basic metadata and the items in the list. 
List kind classes include `PodList`, `NodeList`, `ServiceList`, `EndpointList`, `EventList`, `ReplicationControllerList`, `PersistentVolumeList`, `PersistentVolumeClaimList`, `ServiceAccountList`, `LimitRangeList` and `SecretList`.   

- [Simple kinds](http://kubernetes.io/v1.0/docs/devel/api-conventions.html#lists-and-simple-kinds): There are numerous simple kinds. 

## JSON Mapping

Kubernetes defines specific JSON representations of its types. Skuber implements Play JSON read/write [converters](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators) for mapping between the model classes and their JSON representations. These implicit converters (formatters) can all be made available to your application by a simple import statement.
 
    import skuber.json.format._

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

To interact with the Kubernetes API server:

- Import the API definitions (the simplest way is to `import skuber._`, which will also import the model classes)
- Import the implicit JSON formatters (the API uses these to read/write the request and response data) 
- Ensure a Scala implicit `ExecutionContext` is available
- Create a request context by calling `k8sInit` - this establishes the connection and namespace details for requests to the API
- Invoke the required requests on the API, which generally return their results asynchronously using `Future`s

For example, the following creates the Replication Controller we just parsed above on our Kubernetes cluster:

    // Note: The k8sInit call below uses the default configuration, which (unless overridden) assumes a kubectl proxy is running on localhost:8001
    // and uses the default Kubernetes namespace for all its requests
 
    import skuber._
    import skuber.json.format._
    import scala.concurrent.ExecutionContext.Implicits.global
    val k8s = k8sInit
  
    k8s create controller

When finished making requests the application should call `close` on the request context to release the underlying connection-related resources.
     
### API Method Summary

Create a resource on Kubernetes from a Skuber object kind:

    val rcFut = k8s create controller
    rcFut onSuccess { case rc => println("Created controller, Kubernetes assigned resource version is " rc.metadata.resourceVersion) }

Get a Kubernetes object kind resource by type and name: 

    val rcFut = k8s get[ReplicationController] "guestbook"
    rcFut onSuccess { case rc => println("Current replica count = " + rc.status.get.replicas) }

Get a list of all Kubernetes objects of a given list kind in the current namespace:

    val rcListFut = k8s get[ReplicationControllerList]
    rcFut onSuccess { case rcList => rcList foreach { rc => println(rc.name) } }
    
Update a Kubernetes object kind resource:

    val upscaledController = controller.withReplicas(5)
    val rcFut = k8s update upscaledController
    rcFut onSuccess { case rc => println("Updated controller, new Kubernetes assigned resource version is " + rc.metadata.resourceVersion) }

Delete a Kubernetes object:

    val rmFut = k8s delete[RelicationController] "guestbook"
    rmFut onSuccess { case _ => println("Controller removed") }

Note: There is no support in this alpha release for the Kubernetes API [PATCH operations](http://kubernetes.io/v1.0/docs/devel/api-conventions.html#patch-operations)

### Reactive Watch API

Kubernetes supports the ability for API clients to watch events on specified resources - as changes occur to the resource(s) on the cluster, Kubernetes sends details of the updates to the watching client.  Skuber supports an [Iteratee](https://www.playframework.com/documentation/2.4.x/Iteratees) API for handling these events reactively, providing a `watch` method that returns an `Enumerator` of events which can then be fed to your Iteratee. The following example can be found in the examples sub-project:

    import skuber._
    import skuber.json.format._
  
    import scala.concurrent.ExecutionContext.Implicits.global

    import play.api.libs.iteratee.Iteratee

    object WatchFrontendScaling {
      def run = {
        val k8s = k8sInit    
        val frontendFetch = k8s get[ReplicationController] "frontend"
        frontendFetch onSuccess { case frontend =>
          val frontendWatch = k8s watch frontend
          frontendWatch.events |>>> Iteratee.foreach { frontendEvent => println("Current frontend replicas: " + frontendEvent._object.status.get.replicas) }
        }     
      }
    }

To test the above code, call the run method to create the watch and then separately run a number of [kubectl scale](https://cloud.google.com/container-engine/docs/kubectl/scale) commands to set different replica counts on the frontend - for example:

     kubectl scale --replicas=1 rc frontend
     
     kubectl scale --replicas=10 rc frontend

     kubectl scale --replicas=0 rc frontend

You should see updated statuses being printed out by the Iteratee as the scaling progresses.

The reactive Guestbook example also uses the watch API to support monitoring the progress of deployment steps by watching the status of replica counts.

### Fluent APIi



     

