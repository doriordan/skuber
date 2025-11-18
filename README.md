![Latest Release](https://img.shields.io/badge/Latest%20Release-3.0.0--beta7-red.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/doriordan/skuber/blob/master/LICENSE.txt)

# Skuber
 
Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.

## Features

- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Fluent API for building Kubernetes resources
- Uses standard `kubeconfig` files for configuration - see the [configuration guide](docs/Configuration.md) for details

See the [programming guide](docs/GUIDE.md) for more details.

## A note for Skuber 2 users

For users of Skuber 2, the key updates in this latest version (Skuber 3) are outlined below (see the [migration guide](docs/MIGRATION2to3.md) for full details):

### Pekko Support

The Skuber 2 client had a required transitive dependency on Akka for HTTP client and streaming functionality. 

However, the move of Akka from an Apache 2.0 to a much more restrictive BSL license forced a rethink of this dependency. The result is that Skuber 3 has replaced its required Akka dependencies with configurable dependencies on one of Pekko or Akka, with the Pekko based client likely to be preferred by most Skuber users due to its friendly Apache 2.0 licensing.

### Scala 3

Skuber 3 now supports Scala 3, along with Scala 2.13, and no longer supports Scala 2.12

### Package restructure

While the Skuber client API remains largely unchanged, some imports will need to change as a result of some classes moving to different packages.

## Prerequisites

A Kubernetes cluster is needed at runtime. For local development purposes, `kind` is recommended.

## Quickstart

### Running a Skuber application

The best first step to get started with Skuber is to run one or more of the integration tests against a cluster. There are equivalent integration tests for both the Pekko and Akka clients. To run some integration test locally:

- Ensure you have `sbt` installed

- Clone this repository.

- Configure KUBECONFIG environment variable to point at your cluster configuration file per normal Kubernetes requirements - for example this could be a `kind` cluster running on your laptop.

- Run one or more of the tests, for example:
  ```bash
  sbt:root> integration / testOnly *PekkoDeploymentSpec*
  ```
In this case the code is simply manipulating deployments, but there are a variety of [other tests](integration/src/test/scala/skuber) that demonstrate more of the Skuber API for both the Pekko and Akka based Skuber clients.

### Creating a Skuber application

#### Configuring the build
You can try out the latest Skuber 3 release by adding the required dependencies to your build file - example using `sbt`:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0-beta7"
libraryDependencies += "io.skuber" %% "skuber-pekko" % "3.0.0-beta7"
```

The above dependencies enable your application to create and use a Skuber client that is implemented using Pekko dependencies, this is the default recommended configuration.

#### Implementing An Example

This example lists pods in `kube-system` namespace.

  ```scala
  # Pekko client specific required imports
  import org.apache.pekko.actor.ActorSystem
  import skuber.pekkoclient._

  # Core skuber imports
  import skuber.model._
  import skuber.json.format._

  import scala.util.{Success, Failure}

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Pekko client
  val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
  listPodsRequest.onComplete {
    case Success(pods) => pods.items.foreach { p => println(p.name) }
    case Failure(e) => throw(e)
  }
  ```

The `k8sInit` call returns a concrete Skuber client. Note the Pekko-related imports, which means in this case `k8sInit` is defined in the `skuber.pekkoclient` package and has Pekko dependencies - no Akka dependencies are brought in to the application.
You wil always need to provide an implicit and appropriate `ActorSystem` when using `k8sInit`, as Skuber uses the provided actor system to manage connections etc.

#### Another example: Watching events on the cluster

Skuber supports a number of API methods that utilise streaming for sending or receiving data to or from the Kubernetes API. 

The most notable of these are probably the `watch` methods, which enable cluster events (resource creation, modification and deletion) targeting selected resources to be reactively consumed by the application.

Unlike the non-streaming operations, these streaming API methods expose Pekko specific parameters and results (such as Source return types for `watch` operations) hence the additional Pekko Streams imports below.

```scala
  import org.apache.pekko.actor.ActorSystem
  import org.apache.pekko.stream.KillSwitches
  import org.apache.pekko.stream.scaladsl.{Keep, Sink}
  import skuber.pekkoclient._
  
  import skuber.model.{Container, LabelSelector, Pod}
  import skuber.model.apps.v1.{Deployment, DeploymentList}
  import skuber.api.client.EventType

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Pekko client

 // start watching a couple of deployments
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.getWatcher[Deployment].watch() // this returns a Pekko Streams Source emitting future events on deployment resources 
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
  ```
#### Using the Akka client

The section above shows how to to use a Skuber client that uses Pekko dependencies, which is likely to be the most common case.
However you can instead use Akka dependencies here if desired with some simple changes.

***For most Skuber 3 users it is strongly recommended to use the Pekko client in order to avoid Akka BSL license implications.
Only use the Akka client if you are certain the license implications for your use case are understood.***

To use the Akka-based Skuber client instead of the Pekko one, you just need to make some small build dependency and import changes:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0-beta7"
libraryDependencies += "io.skuber" %% "skuber-akka-bsl" % "3.0.0-beta7"
```

 ```scala
  # Akka client specific required imports
  import akka.actor.ActorSystem
  import skuber.akkaclient._

  # Core skuber imports
  import skuber.model._
  import skuber.json.format._

  import scala.util.{Success, Failure}

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Akka client
  val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
  listPodsRequest.onComplete {
    case Success(pods) => pods.items.foreach { p => println(p.name) }
    case Failure(e) => throw(e)
  }
  ```

Note that the above imports the `k8sInit` call from the `skuber.akkaclient` package, and imports the matching `ActorSystem`, but otherwise the code looks exactly the same as when using the Pekko client.

Similarly for streaming operations we simply swap out the Pekko imports for Akka ones:

```scala 
  # Akka client specific required imports
  import akka.actor.ActorSystem
  import akka.stream.KillSwitches
  import akka.stream.scaladsl.{Keep, Sink}
  import skuber.akkaclient._
  
  # Core Skuber imports
  import skuber.model.{Container, LabelSelector, Pod}
  import skuber.model.apps.v1.{Deployment, DeploymentList}
  import skuber.api.client.EventType

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Akka client, which includes added Akka Streams based ops like `ẁatchAllContinuously`

  // start watching a couple of deployments
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.getWatcher[Deployment].watch()
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
 ```

## Building

Building the library from source is very straightforward. Simply run `sbt test` in the root directory of the project to build the libraries (and examples) and run the unit tests to verify the build. You can then run the integration tests as outlined [here](integration/.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).

##  Legacy Support

Users of Skuber 2 can still use it with the following dependency:

```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.6.7"
```

Skuber 2.x supports Scala 2.12 and 2.13 and has a required transitive dependency on an older, Apache 2.0 licensed version of Akka (2.6.x).

And if you do still need Skuber 2, the [Skuber 2 programming guide](docs/skuber2/GUIDE.md) is still available.

However Skuber is a small open-source project and as such we need to prioritise where effort is being spent. The main effort will be on improving Skuber 3 going forward, and therefore Skuber 2 is basically now in a lower priority maintenance mode, which means: 

- pull requests for Skuber 2 with small but important fixes and key dependency updates are likely to still be accepted
- pull requests for minor enhancements to Skuber 2 will be considered
- major enhancements should be targeted in the first place at Skuber 3, and backported to Skuber 2 only by exception. 
- future releases of Skuber 2 are likely to be less frequent than future releases of Skuber 3, although exceptions may be made for security and other urgent fixes.




