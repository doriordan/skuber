<p align="center"> <img src="docs/skuber_logo.png" alt="skuber logo"> </p>

<p align="center"> Scala client for the <a href="https://kubernetes.io/" target="_blank">Kubernetes API</a>. </p>

</br>


[![Maven Central](https://img.shields.io/maven-central/v/io.github.hagay3/skuber_2.12?color=green&style=for-the-badge)](https://mvnrepository.com/artifact/io.github.hagay3/skuber_2.12)
![Latest release date](https://img.shields.io/github/release-date/hagay3/skuber?style=for-the-badge)
![Commit Activity](https://img.shields.io/github/commit-activity/m/hagay3/skuber?color=green&style=for-the-badge)
[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/byEh56vFJR)

<p align="center">
  <strong>
  <a href="https://skuber.co/" target="_blank">Read the Documentation</a>.
  </strong>
 </p>

</br>

## Quick start

This example lists pods in `kube-system` namespace:

  ```scala
  import skuber._
  import skuber.json.format._
  import org.apache.pekko.actor.ActorSystem
  import scala.util.{Success, Failure}

  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  val k8s = k8sInit
  val listPodsRequest = k8s.list[PodList](Some("kube-system"))
  listPodsRequest.onComplete {
    case Success(pods) => pods.items.foreach { p => println(p.name) }
    case Failure(e) => throw(e)
  }
  ```

## Documentation
Read the [documentation](https://skuber.co) and join [discord community](https://discord.gg/byEh56vFJR) to  ask your questions!


**Note: Since Akka license is no longer an "Open Source” license, the Skuber project moved on to using [Apache Pekko](https://pekko.apache.org), an open-source Akka fork.**

**To help migration from Akka to Pekko, please refer to Pekko's [migration guides](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html).**

**Important: please make sure to rename your `akka` configuration keys to `pekko`. This is important when configuring, e.g., the dispatcher for the application.** 


## Features
- Uses standard `kubeconfig` files for configuration - see the [configuration guide](https://skuber.co/#/?id=configuration) for details
- Scala 3.2, 2.13, 2.12 support
- [Typed Kubernetes Client](https://skuber.co/#/?id=basic-imports) for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster.
- [Dynamic Kubernetes Client](https://skuber.co/#/?id=dynamic-kubernetes-client), which allows you to interact with Kubernetes API without strict types.
- Refreshing EKS tokens [Refresh EKS Token guide](https://skuber.co/#/?id=refresh-eks-aws-token)
- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Fluent API for creating and updating specifications of Kubernetes resources



## Prerequisites

- Java 17
- Kubernetes cluster

A Kubernetes cluster is needed at runtime. For local development purposes, minikube is recommended.
To get minikube follow the instructions [here](https://github.com/kubernetes/minikube)

## Release

You can use the latest release by adding to your build:
- Scala 3.2, 2.13, 2.12 support

```sbt
libraryDependencies += "io.github.hagay3" %% "skuber" % "4.0.0"
```

## Building

Building the library from source is very straightforward. Simply run `sbt test` in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

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


## Support
I'm trying to be responsive to any new issues, you can create github issue or contact me.

Skuber chat on discord: https://discord.gg/byEh56vFJR 

Email: hagay3@gmail.com
