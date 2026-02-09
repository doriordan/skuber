# Skuber Operator Framework Architecture

This document describes the architecture of the Skuber Operator Framework, a Scala-native toolkit for building Kubernetes operators following the controller-runtime/kubebuilder patterns.

## Overview

The framework enables building Kubernetes operators in Scala 3 with:
- Type-safe custom resource definitions via `@customResource` macro
- Reconciler pattern for declarative resource management
- Pekko Streams for backpressure and composable event processing
- Shared caching to reduce API server load
- Leader election for high availability
- Finalizer support for cleanup on deletion

## Design Principles

1. **One Controller per Kind**: Each CRD is managed by exactly one controller, following the single responsibility principle.

2. **Idempotent Reconciliation**: Reconcilers must be idempotent - calling reconcile multiple times with the same input produces the same result.

3. **Level-triggered, not Edge-triggered**: Reconcilers don't know why they were called (create/update/delete). They compare desired state (spec) with actual state and make corrections.

4. **Streaming-first**: Built on Pekko Streams for natural backpressure, error handling, and composability.

5. **Cache-first Reads**: Read operations go through a local cache populated via List+Watch, reducing API server load.

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      ControllerManager                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │   Leader    │  │   Shared    │  │      Controllers        │ │
│  │  Election   │  │    Cache    │  │  ┌───────┐ ┌───────┐   │ │
│  │             │  │             │  │  │Ctrl A │ │Ctrl B │   │ │
│  └─────────────┘  └─────────────┘  │  └───────┘ └───────┘   │ │
│                                     └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Kubernetes API Server                         │
└─────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
skuber.operator/
├── crd/                 # Custom Resource Definition support
│   ├── @customResource  # Macro annotation for CRD definition
│   ├── CustomResourceDef      # Base trait for CRDs with status
│   └── CustomResourceSpecDef  # Base trait for CRDs without status
│
├── reconciler/          # Core reconciliation types
│   ├── Reconciler       # User-implemented business logic
│   ├── ReconcileContext # Client, cache, logger access
│   ├── ReconcileResult  # Done/Requeue/RequeueAfter
│   └── NamespacedName   # Resource identifier
│
├── controller/          # Controller infrastructure
│   ├── Controller       # Manages reconciliation for one Kind
│   ├── ControllerBuilder # Fluent API for construction
│   ├── ControllerManager # Orchestrates multiple controllers
│   ├── WorkQueue        # Deduplicating work queue
│   └── RateLimiter      # Exponential backoff for retries
│
├── cache/               # Caching infrastructure
│   ├── SharedCache      # Manages caches for all types
│   ├── ResourceCache    # Per-type cache interface
│   ├── Reflector        # List+Watch → cache sync
│   └── CacheEvent       # Added/Updated/Deleted events
│
├── finalizer/           # Deletion cleanup support
│   ├── FinalizerSupport # Mixin trait for reconcilers
│   └── FinalizerHandler # Lifecycle management
│
├── leaderelection/      # High availability support
│   └── LeaderElection   # ConfigMap-based leader election
│
└── event/               # Kubernetes event recording
    └── EventRecorder    # Create events for visibility
```

## Key Components

### @customResource Macro

The `@customResource` macro annotation generates boilerplate for custom resources:

```scala
@customResource(
  group = "mycompany.com",
  version = "v1",
  kind = "WebApp"
)
object WebAppResource extends CustomResourceDef[WebAppResource.Spec, WebAppResource.Status]:
  case class Spec(replicas: Int, image: String)
  case class Status(ready: Boolean, availableReplicas: Int)
```

**Generated members:**
- `specFormat: OFormat[Spec]` - JSON serialization for Spec
- `statusFormat: OFormat[St]` - JSON serialization for Status
- `crMetadata` - Tuple of (kind, group, version, plural, singular, shortNames, scope)
- `resourceDefinition` - Implicit `ResourceDefinition[Resource]` for API calls
- `resourceFormat` - Implicit `Format[Resource]` for JSON serialization
- `apply(name, spec)` - Factory method to create resources
- `statusSubresource` - Evidence for status subresource API

### Reconciler

The reconciler contains user business logic:

```scala
trait Reconciler[R <: ObjectResource]:
  def reconcile(resource: R, ctx: ReconcileContext[R]): Future[ReconcileResult]
  def finalize(resource: R, ctx: ReconcileContext[R]): Future[Boolean] = Future.successful(true)
```

**ReconcileResult options:**
- `Done` - Reconciliation complete, no requeue
- `Requeue(reason)` - Requeue immediately
- `RequeueAfter(delay, reason)` - Requeue after delay

### Controller

Controllers watch resources and trigger reconciliation:

```scala
val controller = ControllerBuilder[WebAppResource.Resource](manager)
  .withReconciler(new WebAppReconciler)
  .owns[Deployment]           // Watch owned Deployments
  .owns[Service]              // Watch owned Services
  .watches[ConfigMap](mapper) // Watch related ConfigMaps
  .withConcurrency(4)         // Max parallel reconciliations
  .build()
```

**Event flow:**
```
Watch Events → Deduplicate → Rate Limit → Reconcile → Handle Result
     │              │             │            │            │
     ▼              ▼             ▼            ▼            ▼
  Primary      WorkQueue     RateLimiter   Reconciler   Requeue/Done
  + Owned
  + Watched
```

### Shared Cache

The cache uses the Reflector pattern from controller-runtime:

```
┌──────────────────────────────────────────────────────┐
│                    Reflector                          │
│  1. List all resources                               │
│  2. Populate cache                                   │
│  3. Watch for changes (from resourceVersion)         │
│  4. Apply deltas to cache                            │
│  5. On error/disconnect, restart from step 1 or 3    │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│               InMemoryResourceCache                   │
│  - Thread-safe TrieMap storage                       │
│  - Secondary indexes for efficient queries           │
│  - Owner reference index for getOwnedBy()            │
│  - Event stream for observers                        │
└──────────────────────────────────────────────────────┘
```

### Leader Election

For high availability, only one operator instance should be active:

```scala
val manager = ControllerManager(
  config = ManagerConfig(
    leaderElection = Some(LeaderElectionConfig(
      leaseName = "my-operator",
      leaseNamespace = "my-operator-system",
      identity = sys.env("POD_NAME")
    ))
  ),
  client = client
)
```

**Implementation:**
- Uses ConfigMap as distributed lock (Lease support planned)
- Leader periodically renews claim
- If leader stops renewing, another instance acquires
- Callbacks for `onStartedLeading` and `onStoppedLeading`

### Finalizer Support

Finalizers enable cleanup before deletion:

```scala
class MyReconciler extends Reconciler[MyResource] with FinalizerSupport[MyResource]:
  val finalizerId = FinalizerId("mycompany.com", "cleanup")

  def reconcile(resource: MyResource, ctx: ReconcileContext[MyResource]) =
    FinalizerHandler.handle(resource, ctx, this).flatMap {
      case FinalizerAction.Continue(r) => doReconcile(r, ctx)
      case FinalizerAction.DeletedAndCleaned() => Future.successful(ReconcileResult.Done)
      case FinalizerAction.CleanupPending() => Future.successful(ReconcileResult.RequeueAfter(5.seconds))
      case FinalizerAction.NotOurs() => Future.successful(ReconcileResult.Done)
    }

  override def finalize(resource: MyResource, ctx: ReconcileContext[MyResource]) =
    // Cleanup external resources
    deleteExternalDatabase(resource).map(_ => true)
```

## Data Flow

### Startup Sequence

```
1. ControllerManager.start()
   │
   ├─► LeaderElection.run() [if configured]
   │   └─► Wait to become leader
   │
   ├─► SharedCache.start()
   │   └─► Start Reflector for each registered type
   │       └─► List all resources
   │       └─► Populate cache
   │       └─► Start Watch
   │
   ├─► Cache.waitForSync()
   │   └─► Block until all caches synced
   │
   └─► Controller.start() [for each controller]
       └─► Subscribe to cache events
       └─► Start reconcile loop
```

### Reconciliation Flow

```
1. Cache event received (Added/Updated/Deleted)
   │
2. Extract NamespacedName
   │
3. Add to WorkQueue (deduplicated)
   │
4. WorkQueue.source emits key
   │
5. Fetch resource from cache
   │
6. Call Reconciler.reconcile()
   │
7. Handle result:
   ├─► Done: Mark success, reset rate limiter
   ├─► Requeue: Add back to queue immediately
   ├─► RequeueAfter: Add to delayed queue
   └─► Error: Add with rate-limited backoff
```

## Pekko Streams Integration

The framework leverages Pekko Streams for:

**Backpressure**: If reconciliation is slow, the work queue applies backpressure to prevent unbounded growth.

**Concurrency**: `mapAsync(n)` allows parallel reconciliation with bounded concurrency.

**Restart with backoff**: `RestartSource.withBackoff` handles watch reconnection with exponential backoff.

**Broadcast**: Cache events are broadcast to multiple subscribers via `BroadcastHub`.

```scala
// Simplified reconcile loop
workQueue.source
  .mapAsync(concurrency) { key =>
    reconcileKey(key, cache)
  }
  .via(KillSwitches.single)
  .runWith(Sink.foreach(handleOutcome))
```

## Error Handling

| Error Type | Handling |
|------------|----------|
| Reconcile exception | Caught, logged, requeued with backoff |
| API server error | Retry with exponential backoff |
| Watch disconnect | Auto-reconnect via RestartSource |
| Cache sync timeout | Fail fast on startup |
| Leader election lost | Stop controllers, release resources |

## Best Practices

1. **Keep reconcile idempotent**: Don't assume why reconcile was called.

2. **Reconstitute status from world state**: Don't trust cached status; query actual state.

3. **Use owner references**: Let Kubernetes garbage collect owned resources.

4. **Record events**: Use EventRecorder for significant occurrences.

5. **Handle finalizers early**: Call FinalizerHandler at the start of reconcile.

6. **Set appropriate concurrency**: Balance throughput vs. resource usage.

7. **Use rate limiting**: Prevent hot loops on persistent errors.

## Future Enhancements

- Lease-based leader election (when skuber adds Lease support)
- Admission webhooks (validating/mutating)
- Conversion webhooks (multi-version CRDs)
- Metrics integration (Prometheus)
- Health/readiness probes
- OpenAPI schema generation from case classes
