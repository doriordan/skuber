# NOTE: This branch (for release 2.0 of skuber) is a work in progress!

# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server.

## Features

- Comprehensive set of case classes for representing core and extended Kubernetes resource kinds
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
 
    // Some standard Akka implicits that are required by the skuber client API
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

Note skuber v2.0 is new, and has not yet been extensively tested. It is not officially released, but you can use the latest release candidate. It is available on Maven Central so just add it to your library dependencies e,g, in `sbt`:

    libraryDependencies += "io.github.doriordan" % "skuber_2.12" % "2.0.0-RC1"

Note: the v2.0 release candidate is currently only published for Scala 2.12, but it is planned to cross-publish for Scala 2.11 as well.

The latest Skuber v1.x release, which is the v1.7.1 rC5, can be added instead as follows:

     libraryDependencies += "io.github.doriordan" % "skuber_2.11" % "1.7.1-RC5"
	
Although still in RC status, v1.7.1 is built on the mature Skuber v1 codebase has been quite thoroughly tested against multiple versions of Kubernetes. However Skuber v1.x is only available for Scala 2.11, and Skuber v2 is the strategic version going forward.

## Building

Building the library from source is very straightforward. Simply run `sbt test`in the root directory of the project to build the library (and examples) and run the unit tests to verify the build.

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

## Status

The coverage of the core Kubernetes API functionality by Skuber is comprehensive.

Support of non-core API group functionality is pretty extensive, in particular of the more popular/important features. Deployment, ReplicaSet, StatefulSet, HorizontalPodAutoscaler,Ingress, DaemonSet, Job, CronJob, CustomResourceDefinition and RBAC (Role/RoleBinding/ClusterRole/ClusterRoleBinding) are all currently supported on the master branch. Support for other newer Kubernetes features is being added all the time.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
