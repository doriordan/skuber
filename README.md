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

    import scala.concurrent.ExecutionContext.Implicits.global

    val k8s = k8sInit

    val createOnK8s = for {
      svc <- k8s create nginxService
      rc  <- k8s create nginxController
    } yield (rc,svc)

    createOnK8s onComplete {
      case Success(_) => System.out.println("Successfully created nginx replication controller & service on Kubernetes cluster")
      case Failure(ex) => System.err.println("Encountered exception trying to create resources on Kubernetes cluster: " + ex)
    }

    k8s.close


## Prerequisites

A Kubernetes cluster is needed at runtime, The client has been tested against various releases of Kubernetes, from v1.0 through to v1.7. For local development purposes, minikube is recommended.

You need Java 8 to run Skuber.

## Release

You can include the current Skuber release in your application by adding the following to your `sbt` project:

    resolvers += Resolver.url(
      "bintray-skuber",
      url("http://dl.bintray.com/oriordan/skuber"))(
      Resolver.ivyStylePatterns)

    libraryDependencies += "io.doriordan" %% "skuber" % "1.3.0"

The current release has been built for Scala 2.11 - other Scala language versions such as 2.12 will be supported in future.
The current release version is 1.3.0, which was released for Kubernetes 1.3 so does not support a few recent features of Kubernetes that have since been added to the Skuber master branch.

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
