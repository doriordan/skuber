package skuber

/**
 * Skuber Operator Framework
 *
 * A framework for building Kubernetes operators in Scala, following the
 * controller-runtime/kubebuilder patterns.
 *
 * == Quick Start ==
 *
 * 1. Define your custom resource using the `@customResource` macro:
 * {{{
 * @customResource(group = "mycompany.com", version = "v1", kind = "MyApp")
 * object MyAppResource extends CustomResourceDef[MyAppResource.Spec, MyAppResource.Status]:
 *   case class Spec(replicas: Int, image: String)
 *   case class Status(ready: Boolean)
 * }}}
 *
 * 2. Implement a reconciler:
 * {{{
 * class MyAppReconciler extends Reconciler[MyAppResource.Resource]:
 *   def reconcile(resource: MyAppResource.Resource, ctx: ReconcileContext[MyAppResource.Resource]) =
 *     // Your reconciliation logic here
 *     Future.successful(ReconcileResult.Done)
 * }}}
 *
 * 3. Wire up and run:
 * {{{
 * val manager = ControllerManager(ManagerConfig.default, client)
 *
 * val controller = ControllerBuilder[MyAppResource.Resource](manager)
 *   .withReconciler(new MyAppReconciler)
 *   .owns[Deployment]
 *   .build()
 *
 * manager.add(controller)
 * manager.start()
 * }}}
 *
 * == Key Concepts ==
 *
 * - '''Controller''': Watches resources and triggers reconciliation
 * - '''Reconciler''': Contains your business logic to ensure desired state
 * - '''Cache''': Local cache of resources to reduce API server load
 * - '''Finalizer''': Cleanup logic before resource deletion
 * - '''Leader Election''': Ensures only one operator instance is active
 *
 * == Package Structure ==
 *
 * - [[skuber.operator.crd]] - Custom resource definition macro and traits
 * - [[skuber.operator.controller]] - Controller and manager
 * - [[skuber.operator.reconciler]] - Reconciler traits and types
 * - [[skuber.operator.cache]] - Shared cache infrastructure
 * - [[skuber.operator.finalizer]] - Finalizer support
 * - [[skuber.operator.leaderelection]] - Leader election
 * - [[skuber.operator.event]] - Kubernetes event recording
 */
package object operator:
  // Re-export commonly used types for convenience
  export skuber.operator.reconciler.{
    Reconciler,
    ReconcileContext,
    ReconcileResult,
    ReconcileOutcome,
    NamespacedName,
    OperatorLogger
  }

  export skuber.operator.controller.{
    Controller,
    ControllerBuilder,
    ControllerManager,
    ManagerConfig
  }

  export skuber.operator.cache.{
    SharedCache,
    ResourceCache,
    CacheEvent
  }

  export skuber.operator.finalizer.{
    FinalizerId,
    FinalizerSupport,
    FinalizerHandler,
    FinalizerAction
  }

  export skuber.operator.leaderelection.{
    LeaderElection,
    LeaderElectionConfig
  }

  export skuber.operator.event.{
    EventRecorder,
    EventType
  }
