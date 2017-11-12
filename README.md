
# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.

## Features

- Comprehensive support for Kubernetes API model represented as Scala case classes
- Support for core, extensions and other Kubernetes API groups
- Full support for converting resources between the case class and standard JSON representations 
- Client API for creating, reading, updating, removing, listing and watching resources on a Kubernetes cluster
- The API is asynchronous and strongly typed e.g. `k8s get[Deployment]("nginx")` returns a value of type `Future[Deployment]`
- Fluent API for creating and updating specifications of Kubernetes resources
- Uses standard `kubeconfig` files for configuration - see the [Configuration guide](docs/Configuration.md) for details

See the [programming guide](docs/GUIDE.md) for more details.

## Example Usage

This example creates a nginx service (accessed via port 30001 on each Kubernetes cluster node) that is backed by five nginx replicas.

    import skuber._
    import skuber.json.format._
  
    val nginxSelector  = Map("app" -> "nginx")
    val nginxContainer = Container("nginx",image="nginx").exposePort(80)
    val nginxController= ReplicationController("nginx",nginxContainer,nginxSelector)
    	.withReplicas(5)
    val nginxService = Service("nginx")
    	.withSelector(nginxSelector)
    	.exposeOnNodePort(30001 -> 80) 
 
    // Some standard Akka implicits that are required by the skuber v2 client API
    import akka.actor.ActorSystem
    import akka.stream.ActorMaterializer
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher = system.dispatcher
    
    // Initialise skuber client
    val k8s = k8sInit

    val createOnK8s = for {
      svc <- k8s create nginxService
      rc  <- k8s create nginxController
    } yield (rc,svc)

    createOnK8s onComplete {
      case Success(_) => System.out.println("Successfully created nginx replication controller & service on Kubernetes cluster")
      case Failure(ex) => System.err.println("Encountered exception trying to create resources on Kubernetes cluster: " + ex)
    }

    k8s.close // this prevents any more requests being sent by the client
    system.terminate // this closes the connection resources etc.


## Prerequisites

A Kubernetes cluster is needed at runtime. For local development purposes, minikube is recommended.

You need Java 8 to run Skuber.

## Release

There is a release candidate of version 2.0 available, and it is planned to formally release it before the end of 2017. You can use the latest release candidate (for Scala 2.12) by adding to your build:

    libraryDependencies += "io.skuber" % "skuber_2.12" % "2.0.0-RC2"    

Meanwhile existing users can continue to use the latest (and possibly final, with exception of important fixes) v1.x release:

     libraryDependencies += "io.skuber" % "skuber_2.11" % "1.7.1"

## Migrating to release 2.0

If you have a Skuber client using release 1.x and want to move to the strategic version 2 release, then check out the [migration guide](docs/MIGRATION_1-to-2.md).

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

Note that the master branch has now been updated with the changes for Skuber v2 - if you want to use Skuber v1.x than build from the release_1.x branch instead.

## Quick Start

The quickest way to get started with Skuber:

- If you don't already have Kubernetes installed, then follow the instructions [here](https://github.com/kubernetes/minikube) to install minikube, which is now the recommended way to run Kubernetes locally.

- Ensure Skuber configures itself from the default Kubeconfig file (`$HOME/.kube/config`) : 

	`export SKUBER_CONFIG=file` 

- Try one or more of the examples: if you have cloned this repository run `sbt` in the top-level directory to start sbt in interactive mode and then:

```
    > project examples

    > run
    [warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list

    Multiple main classes detected, select one to run:
    
     [1] skuber.examples.customresources.CreateCRD
     [2] skuber.examples.deployment.DeploymentExamples
     [3] skuber.examples.fluent.FluentExamples
     [4] skuber.examples.guestbook.Guestbook
     [5] skuber.examples.ingress.NginxIngress
     [6] skuber.examples.job.PrintPiJob
     [7] skuber.examples.list.ListExamples
     [8] skuber.examples.scale.ScaleExamples

    Enter number: 
```

For other Kubernetes setups, see the [Configuration guide](docs/Configuration.md) for details on how to tailor the configuration for your clusters security, namespace and connectivity requirements.



## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
