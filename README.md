# Skuber

Skuber is a Scala client library for [Kubernetes](http://kubernetes.io). It provides a high-level and strongly typed Scala API for remotely managing and reactively monitoring Kubernetes resources (such as Pods, Containers, Services, Replication Controllers etc.) via the Kubernetes API server.

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
- Comprehensive Scala case class representations of the Kubernetes types supported by the API server; including `Pod`, `Service`, `ReplicationController`, `Node`, `Container`, `Endpoint`, `Namespace`, `Volume`, `PersistentVolume`,` Resource`, `Security`, `EnvVar`, `ServiceAccount`, `LimitRange`, `Secret`, `Event` and others
- Support for Kubernetes **object**, **list** and **simple** kinds
- Fluent API for building the desired specification ("spec") of a Kubernetes object to be created or updated on the server 
- Complete JSON support for reading and writing the Kubernetes types
- Watching Kubernetes objects and kinds returns Iteratees for reactive processing of events from the cluster
- Highly configurable via kubeconfig files or programmatically
- Support for horizontal pod auto scaling (Kubernetes V1.1 beta feature)

## Getting Started

By default a Skuber client will attempt to connect to the Kubernetes cluster via a [kubectl proxy](http://kubernetes.io/v1.1/docs/user-guide/kubectl/kubectl_proxy.html) running on `localhost:8001`. So just run 

	kubectl proxy& 

to support this default configuration.

However alternatvely Skuber can configure the connection details from the same [kubeconfig](http://kubernetes.io/v1.1/docs/user-guide/kubeconfig-file.html) file format used by `kubectl` and other Kubernetes client. If you have a working Kubernetes installation, you should already have a kubeconfig file so that you do not need to duplicate your existing configuration. 

The $SKUBERCONFIG environment variable must be set in order to use a kubeconfig file.

    export SKUBERCONFIG=file 

The above instructs Skuber to configure itself from the kubeconfig file in the default location *$HOME/.kube/config*

Or you can supply a specific path for the config file using a file URL:

    export SKUBERCONFIG=file:///home/kubernetes_user/.kube/config

After establishing your configuration according to one of the above options, you can verify it by (for example) running the [reactive guestbook](examples/src/main/scala/skuber/examples/guestbook) example.

One benefit of this support for kubeconfig files is that you can use [kubectl config](http://kubernetes.io/v1.1/docs/user-guide/kubectl/kubectl_config.html) to manage the configuration settings.

*(Note: If $SKUBERCONFIG is set then all configuration is loaded from that kubeconfig file - no merging with configuration from other sources occurs)*

## Build Instructions

The project consists of two sub-projects - the main Skuber client library (under the `client` directory) and an `examples` project.

A sbt build file is provided at the top-level directory, so you can use standard sbt commands to build jars for both projects or select one project to build.

## Requirements

- Java 8 (build and run time)
- sbt 0.13 (build time)
- Kubernetes v1.0 or later (run time)

Use of the newer extensions API group features ( currently supported: Scale, HorizontalPodAutoScaler) requires a v1.1 Kubernetes cluster at run time. 

## Security / Authentication

By using the kubeconfig configuraion option, Skuber supports various security/authentication options for its connections with Kubernetes as described below.

If the `kubeconfig` file specifies a **TLS** connection (i.e. a `https://` URL) for the cluster server, Skuber will utilise any server X509 certificate (certificate authority) and/or client X509 certificate/key specified in the configuration file for mutual TLS/SSL authentication with the server - this means there is no need to store the certificate or key data in the Java key store or trust store. 

Skuber respects the`insecure-skip-tls-verify` flag - if it is set to `true` then for  TLS connections the server will be trusted without requiring a server cert. By default this flag is set to false i.e a server certificate is required with TLS connections.

For client authentication Skuber will use any ***bearer token*** or ***basic auth*** credentials specified in the configuration file (bearer token takes precedence over basic auth). Token or basic auth can be used as an alternative to or in conjunction with client certificates.

Configuration can alternatively be passed programmatically to the `k8sInit` call, see the programming guide for details.

## Status

The coverage of the Kubernetes API functionality by Skuber is extensive, however this is an alpha release with all the caveats that implies, including:

- Documentation is currently limited - in practice a basic knowledge of Kubernetes and Scala will be required, from there the Skuber [programming guide](docs/GUIDE.md) and [examples](examples/src/main/scala/skuber/examples) should help get you up and running.

- Support of the [beta features in Kubernetes v1.1](http://blog.kubernetes.io/2015/11/Kubernetes-1-1-Performance-upgrades-improved-tooling-and-a-growing-community.html) currently includes [horizontal pod autoscaling](http://kubernetes.io/v1.1/docs/user-guide/horizontal-pod-autoscaler.html); support for other Kubernetes v1.1 [Extensions API group](http://kubernetes.io/v1.1/docs/api.html#api-groups) features such as [Daemon Sets](http://kubernetes.io/v1.1/docs/admin/daemons.html), [Deployments](http://kubernetes.io/v1.1/docs/user-guide/deployments.html), [Jobs](http://kubernetes.io/v1.1/docs/user-guide/jobs.html) and [Ingress / HTTP load balancing](http://kubernetes.io/v1.1/docs/user-guide/ingress.html) is due shortly.

## License

This code is licensed under the Apache V2.0 license, a copy of which is included [here](LICENSE.txt).
