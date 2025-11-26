# Migrating from Skuber 2 to Skuber 3

## Overview Of Changes in Skuber 3

Skuber 3 is largely backwards compatible with Skuber 2 but with some small changes needed to applications due to significant internal refactor of Skuber 2. Subsequent sections go into more detail on these changes but they can be summarised as follows:

### Users can now choose whether Skuber uses Pekko or Akka

This change is due to the change in Akka licensing from Apache 2.0 to BSL. Akka was a a required dependency on Skuber 2 but now develeopers can choose to use either Pekko (Apache 2.0 licensed) or to still use Akka.

Generally all you need to do to select the client are a couple of simple build and import changes as follows:

#### Pekko Client

Build:
```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0"
libraryDependencies += "io.skuber" %% "skuber-pekko" % "3.0.0"
```

Code:
```scala
  # Pekko client specific imports
  import org.apache.pekko.actor.ActorSystem
  import skuber.pekkoclient._
```

#### Akka Client

Build:
```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0"
libraryDependencies += "io.skuber" %% "skuber-akka-bsl" % "3.0.0"
```

Code:
```scala
  # Akka specific imports
  import akka.actor.ActorSystem
  import skuber.akkaclient._
```

### Core model and api classes are now in different packages 

Skuber 2 provides model as well as many API / configuration related classes in the main `skuber` package. For improved modularity, Skuber 3 segregates these into `skuber.model` and `skuber.api` packages in the core subproject. Generally a small number of import changes is all that is needed to migrate to this modified package structure as the classes themselves are generally unchanged.


### The Watch API has evolved

The previous `watch` operations of Skuber 2 have been replaced by a specific `Watcher` API which offers a set of operations designed to give the full flexibility of the Kubernetes Watch functionality, while still providing convenient access to common use cases.

In Skuber 3 a `Watcher` for a specific resource type is now obtained as follows:
`<client>.getWatcher[<type>]`
For example `k8s.getWatcher[Deployment]` returns an object on which operations can be called to watch Deployment resources.

### Scala Version Upgrade

Skuber 2 supported Scala 2.12 and Scala 2.13. Skuber 3 supports Scala 2.13 and Scala 3 - the rationale is that Scala 2.12 now only has minimal maintenance upgrades, and the community is clearly moving forward to Scala 3 while maintaining good ongoing support for Scala 2.13 for the time being.

Users who require Scala 2.12 for the moment will need to continue to use Skuber 2, which will receive minimal maintenance updates going forward.

## Pekko / Akka Dependency Refactor

Any Kubernetes client needs HTTP client functionality for interacting with the Kubernetes API, and in addition several features of Kubernetes (such as watching events, streaming pod logs and executing commands in pods) are a natural fit for a reactive, streaming API. 

Rather than writing custom code for such common functionality, Skuber 2 uses Akka HTTP and Akka Streams to provide the underlying infrastructure. However, the adoption of BSL licensing by Akka and the related release of Apache Pekko as an Apache licensed Akka fork has given all open source projects that used Akka an important decision to make. 

The decision for Skuber 3 is to give applications the choice to use either Pekko or Akka as the underlying infrastructure for the Kubernetes client - for most use cases Pekko is probably the right choice, but organisations that have commercial licences from Akka may decide to continue to use Akka with Skuber in order to be able to access Akka support and related tools. 

In terms of the Skuber project structure itself, the main implication is that the previous main skuber project has been divided into four subprojects:

- `core`: generic Skuber classes such as the model, json formatters, configuration, base client API and unit tests that have no Akka or Pekko dependencies.
- `pekko`: concrete implementation of the Skuber Kubernetes client API that utilises Pekko for HTTP and streaming.
- `akka`: concrete implementation of the Skuber Kubernetes client API that utilises Akka (BSL licensed version) for HTTP and streaming.
- `integration`: all integration tests (which cover both Pekko and Akka clients) have been moved into an `integration` subproject in accordance with the [latest sbt guidance](https://eed3si9n.com/sbt-1.9.0).

Each of the `core`, `pekko` and `akka` sub-projects now have their own build artefacts, most notably they are packaged as separate libraries. This ensures, for example, that a user can exclude any Akka dependencies from their build when using Skuber with Pekko, an important consideration in light of Akka BSL licensing.

Additionally, the `examples` subproject still exists but is likely to either removed or at least reduced going forward, as the examples are mostly quite old (some even outdated). Much of the original purpose of the examples is now met by the integration tests, which equally demonstrate key client functionality but are well maintained.

## Example Migrations

See the integration tests for many more examples.

### Basic Example: Listing pods in `kube-system` namespace

#### Skuber 2

Build configuration:
```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.6.7"
```

Code:
```scala
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import scala.util.{Success, Failure}

implicit val system = ActorSystem()
implicit val dispatcher = system.dispatcher

val k8s = k8sInit
val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
listPodsRequest.onComplete {
  case Success(pods) => pods.items.foreach { p => println(p.name) }
  case Failure(e) => throw(e)
}
```

#### Skuber 3 (Using Apache Pekko Dependency)

Build:
```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0"
libraryDependencies += "io.skuber" %% "skuber-pekko" % "3.0.0"
```

Code:
```scala
  # Pekko client specific imports
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

#### Skuber 3 (Using Akka BSL Dependency)

Note: as this configuration brings in BSL licensed versions of Akka as transitive dependencies, only use this configuration if you are certain your usage is compliant with the Akka BSL license.

Build:
```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.0.0"
libraryDependencies += "io.skuber" %% "skuber-akka-bsl" % "3.0.0"
```

Code:
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
### Watch Example

This example demonstrates watching deployment events on the cluster, consuming them using Pekko/Akka streams. The build configuration for each Skuber version is as in the previous example.

The Skuber 3 Watcher API has much more to offer - see the [guide](GUIDE.md#reactive-watch-api).

#### Skuber 2

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

  // start watching a couple of deployments starting from their current versions
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

#### Skuber 3 (using Pekko)

```scala 
  import org.apache.pekko.actor.ActorSystem
  import skuber.pekkoclient._
  import org.apache.pekko.stream.KillSwitches
  import org.apache.pekko.stream.scaladsl.{Keep, Sink}
  import skuber.pekkoclient._
  
  import skuber.model.{Container, LabelSelector, Pod}
  import skuber.model.apps.v1.{Deployment, DeploymentList}
  import skuber.api.client.EventType

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Pekko client, which includes added Pekko Streams based ops like those on the watcher

// start watching deployments, filtering for events on specific named ones
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.list[DeploymentList].map { l =>
    k8s.getWatcher[Deployment].watchStartingFromVersion(l.resourceVersion)
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
 ```


#### Skuber 3 (using Akka BSL)

```scala 
  import akka.actor.ActorSystem
  import skuber.akkaclient._
  import akka.stream.KillSwitches
  import akka.stream.scaladsl.{Keep, Sink}
  import skuber.pekkoclient._
  
  import skuber.model.{Container, LabelSelector, Pod}
  import skuber.model.apps.v1.{Deployment, DeploymentList}
  import skuber.api.client.EventType

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit // initializes Skuber Akka client, which includes added Akka Streams based ops like those on the watcher

  // start watching deployments, filtering for events on specific named ones
  val deploymentOneName = ...
  val deploymentTwoName = ...
  val stream = k8s.list[DeploymentList].map { l =>
    k8s.getWatcher[Deployment].watchStartingFromVersion(l.resourceVersion)
            .viaMat(KillSwitches.single)(Keep.right)
            .filter(event => event._object.name == deploymentOneName || event._object.name == deploymentTwoName)
            .filter(event => event._type == EventType.ADDED || event._type == EventType.DELETED)
            .toMat(Sink.collection)(Keep.both)
            .run()
  }
 ```
