# Skuber Cats-Effect Client Guide

Note: This guide covers the beta cats-effect / fs2 / http4s client for Skuber 3. See [GUIDE.md](GUIDE.md) for the main Skuber guide, which covers the data model, JSON mapping, resource building, and other concepts that apply equally to all client backends, as well as the currently supported Akka and Pekko clients.

This guide assumes a working knowledge of Kubernetes concepts, cats-effect, and fs2.

## Overview

The cats-effect client is a fully featured alternative to the Pekko and Akka backends, with effectively a one-to-one mapping of supported operations. It is built on [cats-effect](https://typelevel.org/cats-effect/), [fs2](https://fs2.io/), and [http4s](https://http4s.org/), and exposes a purely functional API.

Key differences from the Pekko/Akka clients:

- Most operations return `F[Either[Status, O]]` (tagless final style) rather than `Future[O]`
- Errors are represented explicitly via `Either[Status, O]` rather than failed `Future`s
- Streaming operations (watches, exec commands, pod logs) return `Stream[F, Either[Status, WatchEvent[O]]]` (fs2 streams)
- The client lifecycle is managed via cats-effect `Resource`, which under the covers manages http4s and fs2 resources.

## Data Model and JSON

The data model and JSON formatters are the same as described in the [main guide](GUIDE.md). The same import patterns apply:

```scala
import skuber.model._
import skuber.json.format._
```

## Using the Cats Kubernetes Client

### Creating a client

The client is created as a cats-effect `Resource`, which handles connection lifecycle automatically. 
While the abstract type of the resource is `F[_]`, normally for production code the concrete type will be `IO` and that is the type used in all these examples. 

```scala
import cats.effect.{IO, IOApp}
import skuber.api.client.{LoggingContext, RequestLoggingContext}
import skuber.catseffect.CatsKubernetesClient

object MyApp extends IOApp.Simple:
  given LoggingContext = RequestLoggingContext()

  def run: IO[Unit] =
    CatsKubernetesClient.resource[IO].use { k8s =>
      // use the k8s client here
      IO.unit
    }
```

The `resource` factory reads configuration from the default kubeconfig (environment variables, `~/.kube/config`, or in-cluster config). A `Network` instance is provided automatically via the cats-effect `IO` runtime.

To use an explicit configuration:

```scala
import skuber.api.Configuration

val config: Configuration = ??? // load your config
CatsKubernetesClient.resource[IO](config).use { k8s => ... }
```

To target a different namespace, call `usingNamespace`:

```scala
val myOtherK8s = k8s.usingNamespace("myOtherNamespace")
```

The `Resource` handles client shutdown automatically when the `use` block completes.

### Basic Client API Usage

All API methods require implicit instances of `Format[O]`, `ResourceDefinition[O]`, and `LoggingContext` to be in scope, just as in the main guide.

Operations return `F[Either[Status, O]]`. A `Left(status)` value indicates a non-OK response from Kubernetes; a `Right(o)` value contains the result.

```scala
import skuber.model.apps.v1.Deployment
import skuber.json.format.*

CatsKubernetesClient.resource[IO].use { k8s =>
  for
    result <- k8s.create(buildNginxDeployment("nginx"))
    depl   <- IO.fromEither(result.left.map(s => new RuntimeException(s.toString)))
    _       = println(s"Created deployment: ${depl.name}")
  yield ()
}

```
See the [cats client integration tests](../cats-it/src/test/scala/skuber/catseffect) for more examples.

### API Method Summary

All methods are defined on `CatsKubernetesClient[F[_]]`.

Get a resource by name:
```scala
val result: IO[Either[Status, Deployment]] = k8s.get[Deployment]("guestbook")
```

Get a resource as an `Option` (returns `None` for 404):
```scala
val result: IO[Option[Deployment]] = k8s.getOption[Deployment]("guestbook")
```

Create a resource:
```scala
val result: IO[Either[Status, Deployment]] = k8s.create(deployment)
```

Update a resource:
```scala
val result: IO[Either[Status, Deployment]] = k8s.update(updatedDeployment)
```

Delete a resource:
```scala
val result: IO[Either[Status, Unit]] = k8s.delete[Deployment]("guestbook")
```

Delete with options (e.g. propagation policy):
```scala
import skuber.api.client.{DeleteOptions, DeletePropagation}

val result: IO[Either[Status, Unit]] =
  k8s.deleteWithOptions[Deployment]("guestbook", DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
```

List all resources of a kind in the current namespace:
```scala
import skuber.model.apps.v1.DeploymentList

val result: IO[Either[Status, DeploymentList]] = k8s.list[DeploymentList]()
```

List with a label selector:
```scala
val result: IO[Either[Status, DeploymentList]] = k8s.listSelected[DeploymentList](labelSelector)
```

List with full options:
```scala
import skuber.api.client.ListOptions

val result: IO[Either[Status, DeploymentList]] = k8s.listWithOptions[DeploymentList](ListOptions(...))
```

Get and update scale subresource:
```scala
val scaleFut = for
  scale    <- k8s.getScale[Deployment]("nginx").map(_.getOrElse(???))
  newScale  = scale.withSpecReplicas(4)
  result   <- k8s.updateScale[Deployment]("nginx", newScale)
yield result
```

Patch a resource (JSON Patch, JSON Merge Patch, or Strategic Merge Patch):
```scala
import skuber.api.patch.{MetadataPatch, JsonMergePatchStrategy}

val patch = MetadataPatch(labels = Some(Map("env" -> "prod")), annotations = None, strategy = JsonMergePatchStrategy)
val result: IO[Either[Status, Pod]] = k8s.patch[MetadataPatch, Pod]("my-pod", patch)
```

Get server API versions:
```scala
val result: IO[Either[Status, List[String]]] = k8s.getServerAPIVersions
```

### Error Handling

Unlike the Pekko/Akka clients, the cats client does not throw `K8SException` for non-OK responses. Instead, errors are returned as `Left(status: Status)`. The `Status` type contains the HTTP status code and an error message from Kubernetes.

```scala
k8s.delete[Deployment]("guestbook").flatMap {
  case Right(_)                                 => IO.println("Deleted")
  case Left(status) if status.code.contains(404) => IO.unit // already gone, ignore
  case Left(status)                             =>
    IO.raiseError(new RuntimeException(s"Delete failed: ${status.message.getOrElse("unknown")}"))
}
```

To convert an `Either` result to an `IO` that raises on error:

```scala
k8s.get[Deployment]("nginx")
  .flatMap(IO.fromEither(_.left.map(s => new RuntimeException(s.toString))))
```

### Reactive Watch API

Watch operations return fs2 `Stream[F, Either[Status, WatchEvent[O]]]`. Each element is either a watch event or a `Status` error from the server.

A `Watcher` is obtained via `k8s.getWatcher[O]` and provides the same watch methods as the main guide's `Watcher` trait.

Watch all objects of a kind in the current namespace (from most recent version):
```scala
import skuber.api.client.{EventType, LoggingContext}
import skuber.json.format.*
import skuber.model.apps.v1.Deployment

k8s.getWatcher[Deployment].watch()
  .collect { case Right(event) => event }
  .evalTap(event => IO.println(s"${event._type}: ${event._object.name}"))
  .compile.drain
```

Watch from a specific resource version (useful to avoid gaps between list and watch):
```scala
for
  listResult <- k8s.list[DeploymentList]()
  currentRV   = listResult.getOrElse(???).resourceVersion
  _          <- k8s.getWatcher[Deployment].watchStartingFromVersion(currentRV)
                  .collect { case Right(e) => e }
                  .take(10)
                  .compile.toList
yield ()
```

Watch a single named object:
```scala
k8s.getWatcher[Deployment].watchObject("my-deployment")
  .collect { case Right(event) => event }
  .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
  .take(2)
  .compile.toList
```

Watch a named object from a specific resource version:
```scala
k8s.getWatcher[Deployment].watchObjectStartingFromVersion("my-deployment", resourceVersion)
```

Watch all objects across the entire cluster (not just current namespace):
```scala
k8s.getWatcher[Pod].watchCluster()
  .collect { case Right(event) => event }
  .evalTap(event => IO.println(s"${event._type}: ${event._object.name}"))
  .compile.drain
```

Watch cluster-wide from a specific version:
```scala
k8s.getWatcher[Deployment].watchClusterStartingFromVersion(resourceVersion)
```

Watch with initial events (streaming list, requires Kubernetes 1.27+):
```scala
// Streams all existing resources as ADDED events, then continues with live changes.
// A BOOKMARK event with annotation "k8s.io/initial-events-end" marks when the initial
// state is complete and live streaming begins.
k8s.getWatcher[Deployment].watchWithInitialEvents()
```

Watch with full control over parameters:
```scala
import skuber.api.client.WatchParameters

val params = WatchParameters(
  labelSelector = Some(labelSelector),
  timeoutSeconds = Some(60)
)
k8s.getWatcher[Pod].watchWithParameters(params)
```

The watch stream reconnects automatically when the server closes the connection (e.g. after a timeout). This enables long-running watches without manual reconnect logic.

### Pod Log Streaming

Pod logs are returned as a `Stream[F, Byte]`. Use fs2 text pipes to decode:

```scala
import fs2.text

k8s.getPodLogStream("my-pod", Pod.LogQueryParams(follow = Some(false)))
  .through(text.utf8.decode)
  .compile.string
  .flatMap(log => IO.println(log))
```

Follow (tail) a pod log, stopping after 30 seconds:
```scala
import scala.concurrent.duration.*

k8s.getPodLogStream("my-pod", Pod.LogQueryParams(follow = Some(true)))
  .through(text.utf8.decode)
  .interruptAfter(30.seconds)
  .compile.string
```

### Exec

Execute a command in a pod. The result is `Stream[F, ExecOutput]` where `ExecOutput` is either `Stdout(data: String)` or `Stderr(data: String)`.

```scala
import skuber.catseffect.ExecOutput

k8s.exec("my-pod", Seq("whoami"))
  .collect { case ExecOutput.Stdout(d) => d }
  .compile.string
  .flatMap(output => IO.println(s"Output: $output"))
```

Specify a container name:
```scala
k8s.exec("my-pod", Seq("ps", "aux"), containerName = Some("nginx"))
```

Read both stdout and stderr:
```scala
k8s.exec("my-pod", Seq("sh", "-c", "echo out; echo err >&2"))
  .compile.toList
  .map { outputs =>
    val stdout = outputs.collect { case ExecOutput.Stdout(d) => d }.mkString
    val stderr = outputs.collect { case ExecOutput.Stderr(d) => d }.mkString
    (stdout, stderr)
  }
```

Interactive exec with stdin and TTY:
```scala
val stdin = fs2.Stream.emit("whoami\n")
k8s.exec("my-pod", Seq("sh"), stdin = Some(stdin), tty = true)
  .interruptAfter(5.seconds)
  .collect { case ExecOutput.Stdout(d) => d }
  .compile.string
```

### Building Resources

Resource construction uses the same fluent API as described in the [main guide](GUIDE.md#building-resources). The model is identical across all client backends.

### Other API Groups

The same API groups (`batch`, `rbac`, `apiextensions.v1`, `networking`, etc.) are available as described in the [main guide](GUIDE.md#other-api-groups).

## Custom Resources

Custom resources are defined and used exactly as described in the [main guide](GUIDE.md#custom-resources). The same `CustomResource`, `ResourceDefinition`, and JSON format definitions apply. The only difference is that the client methods return `F[Either[Status, O]]` instead of `Future[O]`.

## Label Selectors

Label selectors are built using the same mini-DSL described in the [main guide](GUIDE.md#label-selectors):

```scala
import skuber.model.LabelSelector
import LabelSelector.dsl.*

val sel = LabelSelector(
  "tier" is "frontend",
  "release" doesNotExist,
  "env" isNotIn List("production", "staging")
)

val result: IO[Either[Status, PodList]] = k8s.listSelected[PodList](sel)
```

For watch with a label selector, pass it via `WatchParameters`:

```scala
import skuber.api.client.WatchParameters

k8s.getWatcher[Pod].watchWithParameters(WatchParameters(labelSelector = Some(sel)))
```

## Programmatic Configuration

Pass a `Configuration` object directly to `CatsKubernetesClient.resource`:

```scala
import skuber.api.Configuration

val config = Configuration.defaultK8sConfig // or build your own
CatsKubernetesClient.resource[IO](config).use { k8s => ... }
```

See the [main guide](GUIDE.md#programmatic-configuration) for details on the `Configuration` type and kubeconfig parsing.
