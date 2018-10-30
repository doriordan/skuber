[![Build Status](https://travis-ci.org/doriordan/skuber.svg?branch=master)](https://travis-ci.org/doriordan/skuber)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.skuber/skuber_2.12/badge.svg)](http://search.maven.org/#search|ga|1|g:%22io.skuber%22a:%22skuber_2.12%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/doriordan/skuber/blob/master/LICENSE.txt)

# 2.1 PREVIEW

This is a preview of version 2.1 of Skuber. It contains the following changes:

## New advanced controller API (ALPHA)

** Currently incomplete **

This new client API (currently in this [alpha package](client/src/main/scala/skuber/api/alpha/controller)) aims to provide similar functionality for Scala Kubernetes applications as that provided by the `client-go`  library under its `tools/cache` folder to Go Kubernetes applications, as described [here](https://github.com/kubernetes/sample-controller/blob/master/docs/controller-client-go.md)

That `client-go` folder supports a set of components that reflects updates to resources on the cluster into a local (client-side) cache, and informs controllers of changes to those resources. The new Skuber package provides similar funcionality, albeit leveraging Akka streams to simplify and enhance the reactive handling of the flow of updates from the cluster through the cache and informers.  

The primary use case is to make it easier to write custom controllers (as used for example by Kubernetes operators) that are reliably updated with changes on watched set of resources as well as benefitting from local caching of those resources.
  
## Refactoring of `skuber.api` package

This refactoring should not require any changes to client applications but is a significant internal rewrite. The client API has now been factored out into a `KubernetesClient` trait, implemented by `KubernetesClientImpl` class (which is largely the same code as the previous `RequestContext` class, which is now jyst a tye alias to the new client implementation).

Additionally other code in the package,, such the implementation of the pod `exec` method, has been factored out into separate packages/objects.

Overall these changes greatly reduce the size pf the previously bloated `skuber.api.client` package object, and makes the code easier to understand and maintain.  

## ListOptions
 
Full support of [ListOptions](https://godoc.org/k8s.io/apimachinery/pkg/apis/meta/v1#ListOptions), which is a set of options that can be passed with list or watch commands (previously Skuber indirectly supported only some of those options).
 
New `watchWithOptions` and `listWithOptions` client API methods enable any application-specified ListOptions to be passed with `watch` and `list` calls.

Some of the existing `list` and `watch` calls have been internally refactored to use ListOptions in their implementation.
 
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

This example lists pods in `kube-system` namespace:

  ```scala
  import skuber._
  import skuber.json.format._
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import scala.util.{Success, Failure}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit()
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
  import $ivy.`io.skuber::skuber:2.0.12`, skuber._, skuber.json.format._
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

- Java 8
- Kubernetes cluster

A Kubernetes cluster is needed at runtime. For local development purposes, minikube is recommended.
To get minikube follow the instructions [here](https://github.com/kubernetes/minikube)

## Release

You can use the latest release (for Scala 2.11 or 2.12) by adding to your build:

```sbt
libraryDependencies += "io.skuber" %% "skuber" % "2.0.12"
```

Meanwhile users of skuber v1 can continue to use the latest (and possibly final, with exception of important fixes) v1.x release, which is available only on Scala 2.11:

```sbt
libraryDependencies += "io.skuber" % "skuber_2.11" % "1.7.1"
```

## Migrating to release v2

If you have a Skuber client using release v1.x and want to move to the strategic version 2 release, then check out the [migration guide](docs/MIGRATION_1-to-2.md).

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
