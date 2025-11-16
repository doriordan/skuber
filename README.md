[![Build Status](https://travis-ci.org/doriordan/skuber.svg?branch=master)](https://travis-ci.org/doriordan/skuber)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.skuber/skuber_2.12/badge.svg)](http://search.maven.org/#search|ga|1|g:%22io.skuber%22a:%22skuber_2.12%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/doriordan/skuber/blob/master/LICENSE.txt)

# Skuber
 
Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.

NOTE: This README describes Skuber 3. Applications using Skuber 2 can generally easily be migrated to Skuber 3 by changing a few imports. The 
## Features

- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- The API is offered as both Pekko and Akka based variants (from Skuber 3.0)
- Fluent API for creating and updating specifications of Kubernetes resources
- Uses standard `kubeconfig` files for configuration - see the [configuration guide](docs/Configuration.md) for details

See the [programming guide](docs/GUIDE.md) for more details.

## Prerequisites

A Kubernetes cluster is needed at runtime. For local development purposes, `kind` is recommended.

## Skuber 3

The primary changes in Skuber 3 from Skuber 2 are:

- Support for using either a Pekko or Akka based Skuber client (Skuber 2 depends on Akka)
- Support for Scala 3 (Scala 2.13 is also supported, but support for Scala 2.12 is not currently planned).

Skuber 2 depends on Akka (up to version 2.6.x) for its underlying HTTP client functionality, as well as exposing Akka Streams types for some streaming API operations. Due to the migration of the Akka license to BSL, the community requires an alternative that has a more permissive open-source license.

In response to this requirement, Skuber 3 will support a Pekko-based client for most users, while also supporting an Akka-based client for those users who have a commercial license from Lightbend for Akka.

This is implemented by splitting Skuber functionality into three modules/libraries:

- `skuber-core`
  - the base Skuber client API definition (`skuber.api.client.KubernetesClient` trait)
  - other core API types
  - the case class based data model
  - JSON formatters for the data model.

- `skuber-pekko`: implements the Skuber API using Pekko HTTP and Pekko Streams

- `skuber-akka-bsl`: implements the Skuber API using Akka HTTP and Akka Streams. The versions of the Akka dependencies used by this client have a BSL license (because only BSL-licensed Akka versions support Scala 3)

Migrating from Skuber 2 or between the two new clients is generally technically straightforward, requiring some minimal changes to your build (adding the new Skuber core dependency and one of Skuber Pekko or Akka dependencies) and a few changes to `ìmport` statements in your code.

### Using the Pekko-based client

You can try out the latest Skuber 3 beta release (for Scala 2.13 or Scala 3) by adding to your build:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0-beta3"
libraryDependencies += "io.skuber" %% "skuber-pekko" % "3.0.0-beta3"
```

#### Basic Example

This example lists pods in `kube-system` namespace. Note that the as part of the Skuber 3 refactor, the Skuber data model has been moved from the `skuber` to the `skuber.model` package.

  ```scala
  # Pekko specific imports
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
#### Streaming Operation Example

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

  val k8s = k8sInit // initializes Skuber Pekko client, which includes added Pekko Streams based ops like `ẁatchAllContinuously`

 // start watching a couple of deployments
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.list[DeploymentList].map { l =>
    k8s.watchAllContinuously[Deployment](Some(l.resourceVersion))
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
  ```

### Akka Client

***For most Skuber 3 users it is strongly recommended to use the Pekko client in order to avoid Akka BSL license implications.
Only use the Akka client if you are certain the license implications for your use case are understood and acceptable***

To use the Akka-based client add to your dependencies:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0-beta3"
libraryDependencies += "io.skuber" %% "skuber-akka-bsl" % "3.0.0-beta3"
```

#### Basic Akka Client Example

 ```scala
  # Akka specific imports
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

#### Akka Client Streaming Operation Example

```scala 
  import akka.actor.ActorSystem
  import akka.stream.KillSwitches
  import akka.stream.scaladsl.{Keep, Sink}
  import skuber.akkaclient._
  
  import skuber.model.{Container, LabelSelector, Pod}
  import skuber.model.apps.v1.{Deployment, DeploymentList}
  import skuber.api.client.EventType

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Akka client, which includes added Akka Streams based ops like `ẁatchAllContinuously`

  // start watching a couple of deployments
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.list[DeploymentList].map { l =>
    k8s.watchAllContinuously[Deployment](Some(l.resourceVersion))
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
  ```

### Interactive quickstart with sbt

The best way to get quickly started is to run some of the integration tests against a cluster. There are equivalent integration tests for both the Pekko and Akka clients.

- Clone this repository.

- Configure KUBECONFIG environment variable to point at your cluster configuration file per normal Kubernetes requirements. (Check using `kubectl cluster-info`that your cluster is up and running).
    Read more about Skuber configuration [here](docs/Configuration.md)

- Run `sbt`, then select either the `pekko`or `akka` project and run one or more of the integration tests, for example :

  ```bash
  sbt:root> project pekko 
  sbt:skuber-pekko> IntegrationTest/testOnly skuber.DeploymentSpec
  ```
In this case the code is simply manipulating deployments, but there are a variety of other tests that demonstrate more of the Skuber API for both the [Pekko client](pekko/src/it/scala/skuber) and the [Akka client](akka/src/it/scaqla/skuber)

For other Kubernetes setups, see the [configuration guide](docs/Configuration.md) for details on how to tailor the configuration for your clusters security, namespace and connectivity requirements.

##  Skuber 2.0

You can use the latest 2.0 release (for 2.12 or 2.13) by adding to your build:

```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.6.7"
```

Meanwhile users of skuber v1 can continue to use the final v1.x release, which is available only on Scala 2.11:

```sbt
libraryDependencies += "io.skuber" % "skuber_2.11" % "1.7.1"
```

Skuber 2.x supports Scala 2.12 and 2.13 and depends on a non-BSL version of Akka.

NOTE: Skuber 2 supports Scala 2.13 since v2.4.0 - support for Scala 2.11 has now been removed since v2.6.0.

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the libraries (and examples) and run the unit tests to verify the build.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).


