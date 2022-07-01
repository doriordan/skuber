
![Build Status](https://img.shields.io/github/workflow/status/hagay3/skuber/Continuous%20Integration/master?label=Continuous%20Integration&style=for-the-badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.hagay3/skuber_2.12?color=green&style=for-the-badge)](https://mvnrepository.com/artifact/io.github.hagay3/skuber_2.12)
![Latest release date](https://img.shields.io/github/release-date/hagay3/skuber?style=for-the-badge)
![Commit Activity](https://img.shields.io/github/commit-activity/m/hagay3/skuber?color=green&style=for-the-badge)
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/byEh56vFJR)

# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.


## Features
- Uses standard `kubeconfig` files for configuration - see the [configuration guide](docs/Configuration.md) for details
- Refreshing EKS tokens [Refresh EKS Token guide](docs/Refresh_EKS_AWS_Token.md)
- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Fluent API for creating and updating specifications of Kubernetes resources

See the [programming guide](docs/GUIDE.md) for more details.

## Example

This example lists pods in `kube-system` namespace:

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
  import $ivy.`io.github.hagay3::skuber:2.7.6`, skuber._, skuber.json.format._
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

You can use the latest release (for 2.12 or 2.13) by adding to your build:

```sbt
libraryDependencies += "io.github.hagay3" %% "skuber" % "2.7.6"
```

Meanwhile users of skuber v1 can continue to use the final v1.x release, which is available only on Scala 2.11:

```sbt
libraryDependencies += "io.skuber" % "skuber_2.11" % "1.7.1"
```

NOTE: Skuber 2 supports Scala 2.13 since v2.4.0 - support for Scala 2.11 has now been removed since v2.6.0.

## Migrating to release v2

If you have an application using the legacy version v1 of Skuber and want to move to v2, then check out the [migration guide](docs/MIGRATION_1-to-2.md).

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

## CI + Build
The CI parameters defined in `build.sbt`.

ci.yaml and clean.yaml are generated automatically with [sbt-github-actions](https://github.com/djspiewak/sbt-github-actions) plugin.  

Run `sbt githubWorkflowGenerate && bash infra/ci/fix-workflows.sh` in order to regenerate ci.yaml and clean.yaml.

CI Running against the following k8s versions
* v1.19.6
* v1.20.11
* v1.21.5
* v1.22.9
* v1.23.6
* v1.24.1

skuber supports all other k8s versions, not all of them tested under CI.

https://kubernetes.io/releases/



## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).

## Support
I'm trying to be responsive to any new issues, you can create github issue or contact me.

Skuber chat on discord: https://discord.gg/byEh56vFJR 

Email: hagay3@gmail.com
