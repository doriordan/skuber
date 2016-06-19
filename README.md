# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing Kubernetes cluster resources (such as Pods, Services, Deployments, ReplicaSets, Ingresses  etc.) via the Kubernetes REST API server

The client supports v1.0, v1.1 and v1.2 of the Kubernetes API, so should work against all supported Kubernetes releases in use today.

## Example Usage

The code block below illustrates the simple steps required to create a replicated nginx service on a Kubernetes cluster that can be accessed by clients (inside outside the cluster) at a stable address.

It creates a [Replication Controller](http://kubernetes.io/docs/user-guide/replication-controller/) that ensures five replicas ([pods](http://kubernetes.io/docs/user-guide/pods/)) of an nginx container are always running in the cluster, and exposes these to clients via a [Service](http://kubernetes.io/docs/user-guide/services/) that automatically proxies any request received on port 30001 on any node of the cluster to port 80 of one of the currently running nginx replicas.

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

See the [programming guide](docs/GUIDE.md) for more details.


## Features

- Comprehensive Scala case class representations of the numerous Kubernetes kinds such as `Pod`, `Service`, `Container` etc.
- Complete JSON support for reading and writing Kubernetes kinds.
- Support for Kubernetes **object**, **list** and **simple** kinds
- Support for `create`, `get`, `delete`, `list`, `update`, and `watch` methods on Kubernetes kinds.
  - Asynchronous methods (i.e. the methods return [Futures](http://docs.scala-lang.org/overviews/core/futures.html))
  - Each method sends a corresponding RESTful HTTP request to the Kubernetes API server, returning the result in the Future.
  - Strongly-typed API, with the Scala type used to construct requests that target the right kind of Kubernetes resource.
- Support for the extensions API group as well as the core API group.
- Fluent API for building the desired specification ("spec") of a Kubernetes object to be created or updated on the server 
- Watching Kubernetes objects and kinds returns Iteratees for reactive processing of events from the cluster
- Highly [configurable](docs/Configuration.md) via kubeconfig files or programmatically
- Full support for [label selectors](http://kubernetes.io/docs/user-guide/labels), including a mini-DSL for building expressions from both set-based and equality-based requirements.

## Build

There isn't yet a release version of skuber, but building the library and examples from source is very straightforward. 

First - if not already setup on your machine - install the prerequisites:

- [sbt](http://www.scala-sbt.org/) version 0.13 or later
- Java 8

Then: 
 
1. Clone this repository
2. Run `sbt test`in the root directory of the project to build the library (and examples), and run the unit tests to verify the build.

You can then import the library jar you just built into your project e.g. by copying it into your projects lib folder.


## Configure


Before you can run / test an application that uses the Skuber library, you will need access to a Kubernetes cluster - which can be verified by running `kubectl get nodes`, which should list one or more nodes. If you don't have a Kubernetes cluster that you can use, you can follow the instructions [here](http://kubernetes.io/docs/getting-started-guides/) to set one up.

By default a Skuber application will attempt to communicate with the Kubernetes cluster API server listening on port 8080 on the local host, connecting to the default cluster namespace of that cluster without any authentication. See the [Configuration guide](docs/Configuration) for details on how to modify the configuration for your security, namespace and connectivity requirements.

## Examples

Several examples are included under the `examples` sub-project. Thsee illustrate many of the most common usage scenarios for Kubernetes clients.

To test the build and your configuration you can run the examples, the easiest way is to use sbt:

    > project examples

    > run
    [warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list

    Multiple main classes detected, select one to run:

    [1] skuber.examples.deployment.DeploymentExamples
    [2] skuber.examples.fluent.FluentExamples
    [3] skuber.examples.guestbook.Guestbook
    [4] skuber.examples.scale.ScaleExamples
    [5] skuber.examples.ingress.NginxIngress

    Enter number: 

## Status

The coverage of the core Kubernetes API functionality by Skuber is extensive.

Support of more recent extensions group functionality is not yet entirely complete:  full support (with examples) is included for [Deployments](http://kubernetes.io/docs/user-guide/deployments/), [Horizontal pod autoscaling](http://kubernetes.io/docs/user-guide/horizontal-pod-autoscaling/) and [Ingress / HTTP load balancing](http://kubernetes.io/docs/user-guide/ingress/); support for other [Extensions API group](http://kubernetes.io/docs/api/#api-groups) features including [Daemon Sets](http://kubernetes.io/docs/admin/daemons/) and [Jobs](http://kubernetes.io/docs/user-guide/jobs/) is expected shortly.

## More Information

If you have got this far, then the [Programming Guide](docs/GUIDE.md) will take you through the Skuber API in much more detail.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
