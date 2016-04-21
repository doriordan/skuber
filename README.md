# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a fully featured, high-level and strongly typed Scala API for managing and watching Kubernetes resources (such as Pods, Containers, Services, Replication Controllers etc.) in a cluster.

The client supports v1.0 and v1.1 of the Kubernetes REST API.

## Example Usage

The code block below illustrates the simple steps required to create a replicated nginx service on a Kubernetes cluster.
 
The service uses five replicated pods, each running a single Docker container of an nginx image. Each pod publishes the exposed port 80 of its container enabling access to the nginx service within the cluster.

The service can be accessed from outside the cluster at port 30001 on each cluster node, which Kubernetes proxies to port 80 on the nginx pods. 

    import skuber._
    import skuber.json.format._

    val nginxSelector  = Map("app" -> "nginx")
    val nginxContainer = Container("nginx",image="nginx").port(80)
    val nginxController= ReplicationController("nginx",nginxContainer,nginxSelector).withReplicas(5)
    val nginxService   = Service("nginx", nginxSelector, Service.Port(port=80, nodePort=30001)) 

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

- Support for `create`, `get`, `delete`, `list`, `update`, and `watch` operations on Kubernetes types using an asynchronous and type-safe interface that maps each operation to the appropriate Kubernetes REST API requests
- Comprehensive Scala case class representations of the Kubernetes types supported by the API server; including `Pod`, `Service`, `ReplicationController`, `Node`, `Container`, `Endpoint`, `Namespace`, `Volume`, `PersistentVolume`,` Resource`, `Security`, `EnvVar`, `ServiceAccount`, `LimitRange`, `Secret`, `Event`, `Deployment`, `HorizontalPodAutoscaler` and others.
- Support for Kubernetes **object**, **list** and **simple** kinds
- Fluent API for building the desired specification ("spec") of a Kubernetes object to be created or updated on the server 
- Complete JSON support for reading and writing the Kubernetes types
- Watching Kubernetes objects and kinds returns Iteratees for reactive processing of events from the cluster
- Highly configurable via kubeconfig files or programmatically
- Support for V1.1 extensions API group

## Getting Started

Before you start you should have client access to a functioning Kubernetes cluster, which can be tested by running some basic `kubectl` commands e.g. `kubectl get nodes`. If you don't have a Kubernetes cluster that you can use, you can follow the instructions [here](http://kubernetes.io/docs/getting-started-guides/README.html) to set one up.

By default a Skuber client will attempt to connect to the default namespace in the  Kubernetes cluster via a [kubectl proxy](http://kubernetes.io/docs/user-guide/kubectl/kubectl_proxy.html) running on `localhost:8001`. So just run 

	kubectl proxy& 

on the same host(s) as the client to support this default configuration.

However alternatively Skuber can configure the required details for API requests (cluster API server address, authentication, Kubernetes namespace etc.) from the same [kubeconfig](http://kubernetes.io/docs/user-guide/kubeconfig-file.html) file format used by `kubectl` and other Kubernetes client. If you can use `kubectl` you should already have a kubeconfig file so that you do not need to duplicate your existing configuration, or you can use the standard instructions from your Kubernetes provider to create the required file(s). 

The $SKUBERCONFIG environment variable must be set in the clients environment before running the client in order for a kubeconfig file to be used.

    export SKUBERCONFIG=file 

The above results in Skuber configuring itself from the kubeconfig file in the default location *$HOME/.kube/config*

Or you can supply a specific path for the config file using a file URL:

    export SKUBERCONFIG=file:///home/kubernetes_user/.kube/config

After establishing your configuration according to one of the above options, you can verify it by (for example) building and running the examples as described in the next section.

One benefit of this support for kubeconfig files is that you can use [kubectl config](http://kubernetes.io//docs/user-guide/kubectl/kubectl_config.html) to manage the configuration settings.

*(Note: If $SKUBERCONFIG is set then all configuration is loaded from that kubeconfig file - no merging with configuration from other sources occurs)*

## Build Instructions

The project consists of two sub-projects - the main Skuber client library (under the `client` directory) and an `examples` project.

A sbt build file is provided at the top-level directory, so you can use standard sbt commands to build jars for both projects or select one project to build.

To test the build and your configuration you can run the examples, the easiest way is to use sbt:

    > project examples

    > run
    [warn] Multiple main classes detected.  Run 'show discoveredMainClasses' to see the list

    Multiple main classes detected, select one to run:

    [1] skuber.examples.deployment.DeploymentExamples
    [2] skuber.examples.fluent.FluentExamples
    [3] skuber.examples.guestbook.Guestbook
    [4] skuber.examples.scale.ScaleExamples

    Enter number: 

The best example to start with is probably [Guestbook](./examples/src/main/scala/skuber/examples/guestbook/README.md) as it illustrates core Kubernetes / Skuber features.

## Requirements

- Java 8 (build and run time)
- sbt 0.13 (build time)
- Kubernetes v1.0 or later (run time)

Use of the newer extensions API group features ( currently supported: `Deployment`,`Scale`, `HorizontalPodAutoScaler`) requires a v1.1 Kubernetes cluster at run time - the `DeploymentExamples` and `ScaleExamples` examples depend on these features.

## Security / Authentication

With the kubeconfig configuration option Skuber supports standard Kubernetes client security/authentication configuration as described below.

If the `kubeconfig` file specifies a **TLS** connection (i.e. a `https://` URL) to the cluster server, Skuber will utilise the **certificate authority** specified in the kubeconfig file to verify the server (unless the `insecure-skip-tls-verify` flag is set to true, in which case Skuber will trust the server without verification).

For client authentication **client certificates** (cert and private key pairs) can be specified in the configuration file for authenticating the client to the server when using TLS.

Skuber loads the above certificates and keys directly from the kubeconfig file (or from another location in the file system in the case where the configuration of that cert or key specifies a path rather than embedded data). This means there is no need to store them in the Java trust or key stores. 

In addition to client certificates Skuber will use any **bearer token** or **basic authentication** credentials specified in the configuration file. Token or basic auth can be configured as an alternative to or in conjunction with client certificates for client authentication.

All of the above configuration items in the kubeconfig file are the same as used by other Kubernetes clients such as kubectl, so you (or rather the organization deploying the Skuber application) can share configuration with such other clients or set up separate configuration files for the Skuber applications depending on organizational security / deployment policies and other requirements. 

Configuration can alternatively be passed programmatically to the `k8sInit` call, see the programming guide for details.

## Status

The coverage of the Kubernetes API functionality by Skuber is extensive, however this is an alpha release with all the caveats that implies, including:

- Documentation is currently limited - in practice a basic knowledge of Kubernetes and Scala will be required, from there the Skuber [programming guide](docs/GUIDE.md) and [examples](examples/src/main/scala/skuber/examples) should help get you up and running.

- Support of the [beta features in Kubernetes v1.1](http://blog.kubernetes.io/2015/11/Kubernetes-1-1-Performance-upgrades-improved-tooling-and-a-growing-community.html) currently includes [deployments](http://kubernetes.io/docs/user-guide/deployments.html) and [horizontal pod autoscaling](http://kubernetes.io/docs/user-guide/horizontal-pod-autoscaler.html); support for other Kubernetes v1.1 [Extensions API group](http://kubernetes.io/docs/api.html#api-groups) features such as [Daemon Sets](http://kubernetes.io/docs/admin/daemons.html), [Jobs](http://kubernetes.io/docs/user-guide/jobs.html) and [Ingress / HTTP load balancing](http://kubernetes.io/docs/user-guide/ingress.html) is due shortly.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
