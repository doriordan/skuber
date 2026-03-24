# Skuber ZIO Client Guide

Note: This guide covers the beta ZIO / zio-streams / zio-http client for Skuber 3. See [GUIDE.md](GUIDE.md) for the main Skuber guide, which covers the data model, JSON mapping, resource building, and other concepts that apply equally to all client backends, as well as the currently supported Akka and Pekko clients.

This guide assumes a working knowledge of Kubernetes concepts and ZIO.

## Overview

The ZIO client is a fully featured alternative to the other (Pekko/Akka/cats) backends, with effectively a one-to-one mapping of supported operations. It is built on [ZIO](https://zio.dev/) and [zio-http](https://zio.dev/zio-http/), and exposes a purely functional API using ZIO's typed error channel.

Key differences from the Pekko/Akka clients:

- Most operations return `IO[K8sException, O]` rather than `Future[O]`
- Errors are represented in ZIO's typed error channel as `K8SException` rather than failed `Future`s
- Streaming operations (watches, exec commands, pod logs) return `ZStream[Any, K8sException, WatchEvent[O]]` or `ZStream[Any, Throwable, Byte]`
- The client lifecycle is managed via ZIO's `ZLayer`, which handles resource acquisition and release automatically including connection management.

Key differences from the cats-effect client:

- Operations return `IO[K8SException, O]` rather than `F[Either[K8SException, O]]` — errors are in the typed channel, not `Either`
- Watch streams yield `WatchEvent[O]` directly rather than `Either[K8SException, WatchEvent[O]]`
- The watch API uses a single `watch[O](params: WatchParameters)` method instead of separate `getWatcher` + watch variant methods (`getWatcher` may be added in future)
- The service pattern is used - the client is provided as a `ZLayer` and accessed via `ZIO.serviceWithZIO[ZKubernetesClient]`

## Data Model and JSON

The data model and JSON formatters are the same as described in the [main guide](GUIDE.md). The same import patterns apply:

```scala
import skuber.model._
import skuber.json.format._
```

## Using the ZIO Kubernetes Client

### Creating a client

The client is provided as a `ZLayer[Any, Throwable, ZKubernetesClient]`, which handles connection lifecycle automatically.

```scala
import zio.*
import skuber.zio.ZKubernetesClient

object MyApp extends ZIOAppDefault:
  def run: Task[Unit] =
    ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
      // use the k8s client here
      ZIO.unit
    }.provide(ZKubernetesClient.layer)
```

The `layer` factory reads configuration from the default kubeconfig (environment variables, `~/.kube/config`, or in-cluster config).

To use an explicit configuration:

```scala
import skuber.api.Configuration

val config: Configuration = ??? // load your config
ZKubernetesClient.layer(config)
```

To target a different namespace, call `usingNamespace`:

```scala
val myOtherK8s = k8s.usingNamespace("myOtherNamespace")
```

For scoped acquisition (e.g. within a `Scope`):

```scala
val client: ZIO[Scope, Throwable, ZKubernetesClient] = ZKubernetesClient.scoped
```

The `ZLayer` handles client shutdown automatically when the scope closes.

### Basic Client API Usage

All API methods require implicit instances of `Format[O]` and `ResourceDefinition[O]` to be in scope, just as in the main guide.

Operations return `IO[K8sException, O]`. A failed `IO` with `K8sException` indicates a non-OK response from Kubernetes; a successful `IO` contains the result.

```scala
import skuber.model.apps.v1.Deployment
import skuber.json.format.*

ZIO.serviceWithZIO[ZKubernetesClient] { k8s =>
  for
    depl <- k8s.create(buildNginxDeployment("nginx"))
    _     = println(s"Created deployment: ${depl.name}")
  yield ()
}.provide(ZKubernetesClient.layer)
```

See the [ZIO client integration tests](../zio-it/src/test/scala/skuber/zioit) for more examples.

### API Method Summary

All methods are defined on `ZKubernetesClient`.

Get a resource by name:
```scala
val result: IO[K8sException, Deployment] = k8s.get[Deployment]("guestbook")
```

Get a resource as an `Option` (returns `None` for 404):
```scala
val result: IO[K8sException, Option[Deployment]] = k8s.getOption[Deployment]("guestbook")
```

Create a resource:
```scala
val result: IO[K8sException, Deployment] = k8s.create(deployment)
```

Update a resource:
```scala
val result: IO[K8sException, Deployment] = k8s.update(updatedDeployment)
```

Delete a resource:
```scala
val result: IO[K8sException, Unit] = k8s.delete[Deployment]("guestbook")
```

Delete with options (e.g. propagation policy):
```scala
import skuber.api.client.{DeleteOptions, DeletePropagation}

val result: IO[K8sException, Unit] =
  k8s.deleteWithOptions[Deployment]("guestbook", DeleteOptions(propagationPolicy = Some(DeletePropagation.Foreground)))
```

List all resources of a kind in the current namespace:
```scala
import skuber.model.apps.v1.DeploymentList

val result: IO[K8sException, DeploymentList] = k8s.list[DeploymentList]()
```

List with a label selector:
```scala
val result: IO[K8sException, DeploymentList] = k8s.listSelected[DeploymentList](labelSelector)
```

List with full options:
```scala
import skuber.api.client.ListOptions

val result: IO[K8sException, DeploymentList] = k8s.listWithOptions[DeploymentList](ListOptions(...))
```

Get and update scale subresource:
```scala
val scaleIO = for
  scale    <- k8s.getScale[Deployment]("nginx")
  newScale  = scale.withSpecReplicas(4)
  result   <- k8s.updateScale[Deployment]("nginx", newScale)
yield result
```

Patch a resource (JSON Patch, JSON Merge Patch, or Strategic Merge Patch):
```scala
import skuber.api.patch.{MetadataPatch, JsonMergePatchStrategy}

val patch = MetadataPatch(labels = Some(Map("env" -> "prod")), annotations = None, strategy = JsonMergePatchStrategy)
val result: IO[K8sException, Pod] = k8s.patch[MetadataPatch, Pod]("my-pod", patch)
```

Get server API versions:
```scala
val result: IO[K8sException, List[String]] = k8s.getServerAPIVersions
```

### Error Handling

Errors are represented as `K8sException` in ZIO's typed error channel. `K8sException` wraps a `Status` value and provides convenience accessors:

```scala
final case class K8sException(status: Status) extends Exception:
  def code: Option[Int]       = status.code
  def isNotFound: Boolean     = code.contains(404)
  def isConflict: Boolean     = code.contains(409)
  def isUnauthorized: Boolean = code.contains(401)
```

Pattern-match on `K8sException` using `catchSome` or `foldZIO`:

```scala
k8s.delete[Deployment]("guestbook").catchSome {
  case e if e.isNotFound => ZIO.unit // already gone, ignore
}
```

Handle all outcomes with `foldZIO`:

```scala
k8s.get[Deployment]("nginx").foldZIO(
  e       => ZIO.fail(new RuntimeException(s"Get failed: ${e.getMessage}")),
  depl    => ZIO.succeed(depl)
)
```

Retry on conflict (useful for read-modify-write update patterns):

```scala
def retryConflict[T](thunk: => IO[K8sException, T], retries: Int = 5): IO[K8sException, T] =
  thunk.catchSome {
    case e if e.isConflict && retries > 0 =>
      ZIO.sleep(500.milliseconds) *> retryConflict(thunk, retries - 1)
  }

retryConflict(
  k8s.get[Deployment]("nginx").flatMap(d => k8s.update(d.withReplicas(3)))
)
```

### Watch API

Watch operations return `ZStream[Any, K8sException, WatchEvent[O]]`. Events are emitted directly — no `Either` wrapping. The stream reconnects automatically when the server closes the connection (e.g. after a timeout), enabling long-running watches without manual reconnect logic.

All watch variants are expressed through a single `watch[O](params: WatchParameters)` method, with `WatchParameters` controlling the scope and filtering.

Watch all objects of a kind in the current namespace (from most recent version):

```scala
import skuber.api.client.{EventType, WatchParameters}
import skuber.json.format.*
import skuber.model.apps.v1.Deployment

k8s.watch[Deployment]()
  .tap(event => ZIO.debug(s"${event._type}: ${event._object.name}"))
  .runDrain
```

Watch from a specific resource version (useful to avoid gaps between list and watch):

```scala
for
  list      <- k8s.list[DeploymentList]()
  currentRV  = list.resourceVersion
  events    <- k8s.watch[Deployment](WatchParameters(resourceVersion = Some(currentRV)))
                 .take(10)
                 .runCollect
yield events
```

Watch a single named object using a field selector:

```scala
k8s.watch[Deployment](WatchParameters(fieldSelector = Some("metadata.name=my-deployment")))
  .filter(e => e._type == EventType.ADDED || e._type == EventType.DELETED)
  .take(2)
  .runCollect
```

Watch a named object from a specific resource version:

```scala
k8s.watch[Deployment](WatchParameters(
  fieldSelector   = Some(s"metadata.name=$dName"),
  resourceVersion = Some(d.resourceVersion)
))
```

Watch all objects across the entire cluster (not just current namespace):

```scala
k8s.watch[Deployment](WatchParameters(clusterScope = true))
  .tap(event => ZIO.debug(s"${event._type}: ${event._object.name}"))
  .runDrain
```

Watch cluster-wide from a specific version:

```scala
k8s.watch[Deployment](WatchParameters(clusterScope = true, resourceVersion = Some(currentRV)))
```

Watch with a label selector:

```scala
import skuber.model.LabelSelector
import LabelSelector.dsl.*

val sel = LabelSelector("app" is "nginx")
k8s.watch[Pod](WatchParameters(labelSelector = Some(sel)))
```

Watch with a timeout:

```scala
k8s.watch[Pod](WatchParameters(timeoutSeconds = Some(60)))
```

### Pod Log Streaming

Pod logs are returned as a `ZStream[Any, Throwable, Byte]`. Use `ZPipeline.utf8Decode` to decode to strings:

```scala
k8s.getPodLogStream("my-pod", Pod.LogQueryParams(follow = Some(false)))
  .via(ZPipeline.utf8Decode)
  .runFold("")(_ + _)
  .flatMap(log => ZIO.debug(log))
```

Follow (tail) a pod log, stopping after 30 seconds:

```scala
import scala.concurrent.duration.*

k8s.getPodLogStream("my-pod", Pod.LogQueryParams(follow = Some(true)))
  .via(ZPipeline.utf8Decode)
  .interruptAfter(30.seconds)
  .runFold("")(_ + _)
```

### Exec

Execute a command in a pod. The result is `ZStream[Any, Throwable, ExecOutput]` where `ExecOutput` is either `Stdout(data: String)` or `Stderr(data: String)`.

```scala
import skuber.zio.ExecOutput

k8s.exec("my-pod", Seq("whoami"))
  .collect { case ExecOutput.Stdout(d) => d }
  .runFold("")(_ + _)
  .flatMap(output => ZIO.debug(s"Output: $output"))
```

Specify a container name:

```scala
k8s.exec("my-pod", Seq("ps", "aux"), containerName = Some("nginx"))
```

Read both stdout and stderr:

```scala
k8s.exec("my-pod", Seq("sh", "-c", "echo out; echo err >&2"))
  .runCollect
  .map { outputs =>
    val stdout = outputs.collect { case ExecOutput.Stdout(d) => d }.mkString
    val stderr = outputs.collect { case ExecOutput.Stderr(d) => d }.mkString
    (stdout, stderr)
  }
```

Interactive exec with stdin and TTY:

```scala
val stdin = ZStream.succeed("whoami\n")
k8s.exec("my-pod", Seq("sh"), stdin = Some(stdin), tty = true)
  .interruptAfter(5.seconds)
  .collect { case ExecOutput.Stdout(d) => d }
  .runFold("")(_ + _)
```

### Building Resources

Resource construction uses the same fluent API as described in the [main guide](GUIDE.md#building-resources). The model is identical across all client backends.

### Other API Groups

The same API groups (`batch`, `rbac`, `apiextensions.v1`, `networking`, etc.) are available as described in the [main guide](GUIDE.md#other-api-groups).

## Custom Resources

Custom resources are defined and used exactly as described in the [main guide](GUIDE.md#custom-resources). The same `CustomResource`, `ResourceDefinition`, and JSON format definitions apply. The only difference is that the client methods return `IO[K8sException, O]` instead of `Future[O]`.

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

val result: IO[K8sException, PodList] = k8s.listSelected[PodList](sel)
```

For watch with a label selector, pass it via `WatchParameters`:

```scala
import skuber.api.client.WatchParameters

k8s.watch[Pod](WatchParameters(labelSelector = Some(sel)))
```

## Programmatic Configuration

Pass a `Configuration` object directly to `ZKubernetesClient.layer`:

```scala
import skuber.api.Configuration

val config = Configuration.defaultK8sConfig // or build your own
ZKubernetesClient.layer(config)
```

See the [main guide](GUIDE.md#programmatic-configuration) for details on the `Configuration` type and kubeconfig parsing.
