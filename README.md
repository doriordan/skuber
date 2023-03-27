[![Build Status](https://travis-ci.org/doriordan/skuber.svg?branch=master)](https://travis-ci.org/doriordan/skuber)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.skuber/skuber_2.12/badge.svg)](http://search.maven.org/#search|ga|1|g:%22io.skuber%22a:%22skuber_2.12%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/doriordan/skuber/blob/master/LICENSE.txt)


# Skuber
 
Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.

## Features

- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Fluent API for creating and updating specifications of Kubernetes resources
- Uses standard `kubeconfig` files for configuration - see the [configuration guide](docs/Configuration.md) for details

See the [programming guide](docs/GUIDE.md) for more details.

## Example

This example (for Skuber 3.x) lists pods in `kube-system` namespace:

  ```scala
  import skuber._
  import skuber.json.format._
  import org.apache.pekko.actor.ActorSystem
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

  See more elaborate example [here](docs/Examples.md).

## Quick Start

Make sure [prerequisites](#prerequisites) are met. There are couple of quick ways to get started with Skuber:

### With [Ammonite-REPL](http://ammonite.io/#Ammonite-REPL)

Provides you with a configured client on startup. It is handy to use this for quick experiments.

- using bash

  ```bash
  $ amm -p ./Quickstart.sc
  ```

- from inside ammonite-repl:

  ```scala
  import $file.`Quickstart`, Quickstart._
  ```

  > Just handy shortcut to import skuber inside ammonite-repl:

  ```scala
  import $ivy.`io.skuber::skuber:2.6.7`, skuber._, skuber.json.format._
  ```

### Interactive with sbt

- Clone this repository.

- Tell Skuber to configure itself from the default Kubeconfig file (`$HOME/.kube/config`):

    ```bash
    export SKUBER_CONFIG=file
    ```

    Read more about Skuber configuration [here](docs/Configuration.md)

- Run `sbt` and try  one or more of the [examples](./examples/src/main/scala/skuber/examples) and then:

  ```bash
  sbt:root> project examples
  sbt:skuber-examples> run

  Multiple main classes detected, select one to run:

   [1] skuber.examples.customresources.CreateCRD
   [2] skuber.examples.deployment.DeploymentExamples
   [3] skuber.examples.fluent.FluentExamples
   [4] skuber.examples.guestbook.Guestbook
   [5] skuber.examples.ingress.NginxIngress
   [6] skuber.examples.job.PrintPiJob
   [7] skuber.examples.list.ListExamples
   [8] skuber.examples.patch.PatchExamples
   [9] skuber.examples.podlogs.PodLogExample
   [10] skuber.examples.scale.ScaleExamples
   [11] skuber.examples.watch.WatchExamples

  Enter number:
  ```

For other Kubernetes setups, see the [configuration guide](docs/Configuration.md) for details on how to tailor the configuration for your clusters security, namespace and connectivity requirements.

## Prerequisites

- Java 8 or higher
- Kubernetes cluster

A Kubernetes cluster is needed at runtime. For local development purposes, `kind` is recommended.
To install `kind` follow the instructions [here](https://kind.sigs.k8s.io/docs/user/quick-start/).

## Release

You can use the latest full 2.x release (for Scala 2.12 or 2.13) by adding to your build:

```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.6.7"
```

Meanwhile a pre-release of 3.x (which moves away from Akka to Pekko) is now available:

```sbt
libraryDependencies += "io.skuber" % "skuber" % "3.0.0-beta1"
```

The pre-release currently uses a nightly build of Pekko as no full release is yet available - as such it is not recommended for production use but can be used for testing migration of your stack away from Akka. 
A full release of Skuber 3.x will be made available when Pekko has an official release.

NOTE: Skuber supports Scala 2.13 since 2.4.0 - support for Scala 2.11 has now been removed since 2.6.0.

## Migrating from release 2.x to 3.x

Skuber 3.x is mostly backwards-compatible with 2.x, except that it replaces all of its uses of Akka with [Pekko](https://github.com/apache/incubator-pekko). 
In practice this normally requires minimal changes to your application to migrate to 3.x:

- rename Akka imports e.g. `import akka.actor.ActorSystem` becomes `import org.apache.pekko.actor.ActorSystem`
- rename any `akka` section(s) of your application configuration (`application.conf` file) that relate to skuber to `pekko`.

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).

## IMPORTANT: Akka License Model Changes And Pekko Migration

Lightbend have moved Akka versions starting from 2.7.x from an Apache 2.0 to BSL license. Skuber 2.x uses Akka 2.6.x and it is not planned to move to a BSL licensed Akka version - instead it is planned that Skuber 3.x will move from Akka to the Apache Pekko open-source fork.