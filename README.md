![Latest Release](https://img.shields.io/badge/Latest%20Release-3.1.0-red.svg)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/doriordan/skuber/blob/master/LICENSE.txt)

***Announcement*** Looking to build a Kubernetes-native operator or controller in Scala? The new [skuber-operator](https://github.com/doriordan/skuber-operator) project builds on Skuber to offer a fully-featured Operator SDK similiar to those offered in other languages.

# Skuber
 
Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, StatefulSets, Ingresses, Roles etc.) via the Kubernetes REST API server.

## Features

- Comprehensive support for Kubernetes API model represented as Scala case classes
- Full support mapping between the model and the required Kubernetes JSON representations for the API
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s.get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Optional fluent API for building common Kubernetes resource types
- Reuse existing `kubeconfig` files (via KUBECONFIG environment variable) for the client configuration without modification
- No need for explicit configuration when run inside a pod - the client detects its environment and connects automatically to the cluster API server, periodically refreshing the access token used
- Choice of client implementations - the most commonly used one uses Pekko under the hood, but there is a swappable alternative that uses Akka instead, and a beta cats-effect/fs2 client for the Typelevel stack
- Supports Scala 3 and Scala 2
- (Experimental) Forms a foundation for building Kubernetes operators using [skuber-operator](https://github.com/doriordan/skuber-operator) 

See the [latest programming guide](docs/GUIDE.md) for more details.

## A note for Skuber 2 users

For users of Skuber 2, the key updates in this latest version (Skuber 3) are outlined in the [migration guide](docs/MIGRATION2to3.md).
An important change worth highlighting here is that the required dependency on Akka in Skuber 2 has been replaced by a configurable dependency on either Pekko or Akka - see the migration guide for more details.

## Prerequisites

A Kubernetes cluster is needed at runtime. For local development purposes, `kind` is recommended.

## Quickstart

### Running a Skuber application

The best first step to get started with Skuber is to try it out - by running the included ammonite REPL and/or one or more of the integration tests against a cluster. There are equivalent integration tests for both the Pekko and Akka clients. First carry out basic setup tasks:

- Clone this repository.

- Configure KUBECONFIG environment variable to point at your cluster configuration file per normal Kubernetes requirements - for example this could be a `kind` cluster running on your laptop.

#### Ammonite

You can run Ammonite via the included script, which predefines a Skuber Pekko client:

```bash
✗ ./repl/amm
Loading...
...
============================================
 Skuber REPL - Pekko Client Initialized
============================================
 Available:
   k8s    - PekkoKubernetesClient (default namespace)
   system - ActorSystem
   ec     - ExecutionContext

 Quick examples:
   Await.result(k8s.list[PodList](), 30.seconds)
   Await.result(k8s.get[Deployment]("name"), 30.seconds)
   k8s.usingNamespace("kube-system")

 Cleanup when done:
   k8s.close()
   system.terminate()
============================================

Welcome to the Ammonite Repl 3.0.8 (Scala 3.3.7 Java 17.0.8.1)
```
Now try out a simple Skuber request:
```bash
@ Await.result(k8s.list[PodList](), 30.seconds)
```
You should see results that look like:
```bash
[INFO] [03/09/2026 08:06:30.270] [skuber-repl-pekko.actor.default-dispatcher-6] [skuber.api] [ { reqId=84075e15-56c8-453d-8e9a-c18ac8bc148a} } - about to send HTTP request: GET https://127.0.0.1:56166/api/v1/namespaces/default/pods]
[INFO] [03/09/2026 08:06:30.435] [skuber-repl-pekko.actor.default-dispatcher-12] [skuber.api] [ { reqId=84075e15-56c8-453d-8e9a-c18ac8bc148a} } - received response with HTTP status 200]
res0: ListResource[Pod] = ListResource(
  apiVersion = "v1",
  kind = "PodList",
  metadata = Some(
    value = ListMeta(
      selfLink = "",
      resourceVersion = "4274434",
      continue = None
    )
  ),
  items = List()
)
```
#### Running Integration Test(s)

First ensure you have `sbt` installed and run it in this repository. You can then run any or all integration tests in the standard way:

```bash
 ✗ sbt
  ...
  sbt:root> integration / testOnly *PekkoDeploymentSpec*
  ```
In this case the code is simply manipulating deployments, but there are a variety of [other tests](integration/src/test/scala/skuber) that demonstrate more of the Skuber API for both the Pekko and Akka based Skuber clients.

### Creating a Skuber application

#### Configuring the build
to create an application that uses Skuber, you should start by adding the required dependencies to the project build file - example using `sbt`:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.1.0"
libraryDependencies += "io.skuber" %% "skuber-pekko" % "3.1.0"
```

The above dependencies enable your application to create and use a Skuber client that is implemented using Pekko, this is the default recommended configuration.

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
  k8s.close()
  system.terminate()
  ```

The `k8sInit` call returns a concrete Skuber client which is then used to make the requests to the Kubernetes cluster API.

#### Using the Akka client

The section above shows how to to use a Skuber client that is based on Pekko, which is likely to be the most common case.
However you can instead use an Akka-based client here if desired by making a few simple changes.

***For most Skuber 3 users it is strongly recommended to use the Pekko client in order to avoid Akka BSL license implications.
Only use the Akka client if you are certain the license implications for your use case are understood.***

To use the Akka-based Skuber client instead of the Pekko one, you just need to make some small build dependency and import changes:

```sbt
libraryDependencies += "io.skuber" %% "skuber-core" % "3.1.0"
libraryDependencies += "io.skuber" %% "skuber-akka-bsl" % "3.1.0"
```

 ```scala
  # Akka client specific required imports
  import akka.actor.ActorSystem
  import skuber.akkaclient._

  // the rest of the code should look just the same as the Pekko example
  ```

#### Using the Cats Effect client

The `cats-effect` client is new and has no release yet, this page will be updated with dependency details once a release has been published.

```scala
import cats.effect.{IO, IOApp}
import skuber.catseffect.CatsKubernetesClient
import skuber.api.client.LoggingContext

object MyApp extends IOApp.Simple:
  given LoggingContext = LoggingContext.lc

  def run: IO[Unit] =
    CatsKubernetesClient.resource[IO].use { k8s =>
      k8s.list[PodList]().flatMap {
        case Right(pods) => IO.println(pods.items.map(_.name).mkString(", "))
        case Left(err)   => IO.println(s"Error: ${err.message}")
      }
    }
```

The cats client uses a `Resource`-based lifecycle (underlying resources will be closed down automatically when done), returns `F[Either[Status, O]]` instead of throwing exceptions, and uses `fs2.Stream` for streaming operations. See the [cats client programming guide](docs/GUIDE_CATS.md) for full details.

## Building

Building the library from source is very straightforward. Simply run `sbt test` in the root directory of the project to build the libraries (and examples) and run the unit tests to verify the build. You can then run the integration tests as outlined [here](integration/src/test/scala/skuber/README.md).

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).

## Kubernetes Operators

A common advanced use case for Kubernetes applications are operators and controllers. The new [skuber-operator](https://github.com/doriordan/skuber-operator) project provides an SDK for building these on top of Skuber.

##  Legacy Support

Users of Skuber 2 can still use it with the following dependency:

```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.6.8"
```

Skuber 2.x supports Scala 2.12 and 2.13 and has a required transitive dependency on an older, Apache 2.0 licensed version of Akka (2.6.x).

And if you do still need Skuber 2, the [Skuber 2 programming guide](docs/skuber2/GUIDE.md) is still available.

However Skuber is a small open-source project and as such we need to prioritise where effort is being spent. The main effort will be on improving Skuber 3 going forward, and therefore Skuber 2 is basically now in a lower priority maintenance mode.

## Contributing

Pull requests are generally welcome.

Please note pull requests should normally be for Skuber 3 (on the default `3.1.x` branch) going forward. For a limited period of time pull requests for Skuber 2 (`2.6.x` branch) with small but important fixes and key dependency updates are likely to still be accepted, but more complex and/or less urgent changes are really encouraged to be targetted at Skuber 3, especially as migration from Skuber 2 to Skuber 3 should be straightforward for most users.


