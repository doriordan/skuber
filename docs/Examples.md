# Skuber usage examples

Skuber is built on top of Akka HTTP and therefore it is non-blocking and concurrent by default.
Almost all requests return a Future, and you need to write a little bit of extra code if you want quick
experiments in a single-threaded environment (like Ammonite REPL, or simple tests)
It all boils down to either using Await or onComplete - see examples below.

## Basic imports

```scala
import skuber._
import skuber.json.format._


// Some standard Akka implicits that are required by the skuber v2 client API
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
implicit val dispatcher = system.dispatcher
```

## Populate cluster access configuration and initialize client

You can configure cluster in [many different ways](Configuration.md). This example
directly calls method that reads kubeconfig file at default location.
Check [kubernetes docs](https://kubernetes.io/docs/tasks/access-application-cluster/configure-access-multiple-clusters/#before-you-begin) if you don't know what is kubeconfig or where to look for it.

```scala
import api.Configuration

// assumes that Success is returned.
val cfg: Configuration = Configuration.parseKubeconfigFile().get
val k8s = k8sInit(cfg)
```

## List pods example

Here we use `k8s` client to get all pods in `kube-system` namespace:

```scala
import scala.util.{Success, Failure}
val listPodsRequest = k8s.listInNamespace[PodList]("kube-system")
listPodsRequest.onComplete {
  case Success(pods) => pods.items.foreach { p => println(p.name) }
  case Failure(e) => throw(e)
}
```

## List Namespaces

```scala
import scala.concurrent.Await
import scala.concurrent.duration._

val list = Await.result(k8s.list[NamespaceList], 10.seconds).items.map(i => i.name)
// res19: List[String] = List("default", "kube-public", "kube-system", "namespace2", "ns-1")

```


## Create Pod

```scala
import scala.concurrent.Await
import scala.concurrent.duration._

val podSpec     = Pod.Spec(List(Container(name = "nginx", image = "nginx")))
val pod         = Pod("nginxpod", podSpec)
val podFuture   = k8s.create(pod)
// handle future as you see fit
```

## Create deployment

This example creates a nginx service (accessed via port 30001 on each Kubernetes cluster node) that is backed by a deployment of five nginx replicas.
 Requires defining selector, container description, pod spec, deployment and service:

```scala
// Define selector
import LabelSelector.dsl._
val nginxSelector  = "app" is "nginx"

// Define nginx container
val nginxContainer = Container(name = "nginx", image = "nginx")
  .exposePort(80)

// Define nginx pod template spec
val nginxTemplate = Pod.Template.Spec
  .named("nginx")
  .addContainer(nginxContainer)
  .addLabel("app" -> "nginx")

// Define nginx deployment
import skuber.apps.v1.Deployment
val nginxDeployment = Deployment("nginx")
  .withReplicas(5)
  .withTemplate(nginxTemplate)
  .withLabelSelector(nginxSelector)

// Define nginx service
val nginxService = Service("nginx")
  .withSelector("app" -> "nginx")
  .exposeOnNodePort(30001 -> 80)

// Create the service and the deployment on the Kubernetes cluster
val createOnK8s = for {
  svc  <- k8s create nginxService
  dep  <- k8s create nginxDeployment
} yield (dep,svc)

createOnK8s.onComplete {
  case Success(_)  => println("Successfully created nginx deployment & service on Kubernetes cluster")
  case Failure(ex) => throw (new Exception("Encountered exception trying to create resources on Kubernetes cluster: ", ex))
}
```

## Execute a job and monitor its execution until completed (successfully or not) and monitor its deletion

First, define a suitable progress predicate for monitoring the execution of a Kubernetes pod.
For example:

```scala
  def podProgress(
      ev: WatchEvent[Pod]
  ): Boolean = {

    def containerStatusProgress(acc: Boolean, x: Container.Status): Boolean = {
      x.state.fold[Boolean](acc) {
        case Container.Waiting(None) => acc
        case Container.Waiting(Some(reason)) =>
          !(reason.startsWith("Err") || reason.endsWith("BackOff"))
        case Container.Running(_)    => acc
        case _: Container.Terminated => false
      }
    }

    def podStatusProgress(
        s: Pod.Status
    ): Boolean = {
      val ok1 = s.initContainerStatuses
        .foldLeft[Boolean](true)(containerStatusProgress)
      val ok2 = s.containerStatuses
        .foldLeft[Boolean](ok1)(containerStatusProgress)
      val ok3 = s.conditions.foldLeft[Boolean](ok2) {
        case (acc, _: Pod.Condition) =>
          acc
      }
      ok3
    }

    ev._type != EventType.DELETED &&
    ev._type != EventType.ERROR &&
    ev._object.status.fold[Boolean](true)(podStatusProgress)
  }
```

Next, define a suitable completion callback for handling a completed.
For example:

```scala
  def podCompletion(k8s: KubernetesClient)(lastPodEvent: WatchEvent[Pod])(
      implicit ec: ExecutionContext,
      mat: ActorMaterializer): Future[Unit] = {

     def printLogFlow(cntrName: String): Sink[ByteString, Future[Done]] =
      Flow[ByteString]
        .via(
          Framing.delimiter(ByteString("\n"),
                            maximumFrameLength = 10000,
                            allowTruncation = true))
        .map(_.utf8String)
        .toMat(Sink.foreach(text => println(s"[$cntrName logs] $text")))(Keep.right)

    def showContainerStateIfSuccessful(cs: Container.Status,
                                       podName: String,
                                       message: String): Future[Unit] = {
      val terminatedSuccessfully = cs.state.foldLeft[Boolean](false) {
        case (_, s: Container.Terminated) =>
          0 == s.exitCode
        case (flag, _) =>
          flag
      }

      if (terminatedSuccessfully)
        for {
          logSource <- k8s.getPodLogSource(
            name = podName,
            queryParams = Pod.LogQueryParams(containerName = Some(cs.name)))
          _ <- logSource.runWith(printLogFlow(message))
        } yield ()
      else {
        println(s"$message: no output because of unsuccessful execution")
        Future.successful(())
      }
    }

    lastPodEvent._object.status match {
      case None =>
        Future.successful(())
      case Some(s) =>
        val podName = lastPodEvent._object.name
        for {
          _ <- s.initContainerStatuses
            .foldLeft[Future[Unit]](Future.successful(())) {
              case (_, cs) =>
                showContainerStateIfSuccessful(
                  cs,
                  podName,
                  s"init/$podName (iteration=${lastPodEvent._object.metadata.labels("iteration")})")
            }
          _ <- s.containerStatuses
            .foldLeft[Future[Unit]](Future.successful(())) {
              case (_, cs) =>
                showContainerStateIfSuccessful(
                  cs,
                  podName,
                  s"$podName (iteration=${lastPodEvent._object.metadata.labels("iteration")})")
            }
        } yield ()
    }
  }
``` 

Next, define some suitable delays for monitoring:

```scala
val watchContinuouslyRequestTimeout: Duration = ...
val deletionMonitorRepeatDelay: FiniteDuration = ...
```

There are different strategies to execute jobs.

- Sequentially

    Define a list of jobs to execute.
    This example generates a sequence of jobs, some that cannot be executed.
    
    ```scala
     val jobs = Seq.tabulate[Job](n = 10) { n =>
          if (n % 3 == 0) {
            // simulate a job failure
            val piContainer = Container(name = "pi",
                                        image = "nowhere/does-not-exist:latest",
                                        command = List("/bin/bash"),
                                        args = List("-c", "env"))
            val piSpec = Pod
              .Spec()
              .addContainer(piContainer)
              .withRestartPolicy(RestartPolicy.Never)
            val piTemplateSpec =
              Pod.Template.Spec(metadata = metadata(n)).withPodSpec(piSpec)
            Job("pi").withTemplate(piTemplateSpec)
          } else {
            val piContainer = Container(
              name = "pi",
              image = "perl",
              command =
                List("perl", "-Mbignum=bpi", "-wle", s"print bpi(${n * 10})"))
            val piSpec = Pod
              .Spec()
              .addContainer(piContainer)
              .withRestartPolicy(RestartPolicy.Never)
            val piTemplateSpec =
              Pod.Template.Spec(metadata = metadata(n)).withPodSpec(piSpec)
            Job("pi").withTemplate(piTemplateSpec)
          }
        }
    ```

    Execute the first job without a host connection pool
    and reuse the pool obtained for executing subsequent jobs.
    Finally, shutdown the connection pool.
        
    ```scala
        val (firstJob, otherJobs) = (jobs.head, jobs.tail)
    
        val f: Future[Unit] = for {
    
          // First run: create a pool.
          (pool, hcp, podEvent) <- k8s.executeJobAndWaitUntilDeleted(
            firstJob,
            labelSelector,
            podProgress,
            podCompletion(k8s),
            watchContinuouslyRequestTimeout,
            deletionMonitorRepeatDelay,
            None)
    
          // Subsequent runs: reuse the same pool.
          _ <- Source
            .fromIterator(() => otherJobs.toIterator)
            .mapAsync(parallelism = 1) { job: Job =>
              k8s.executeJobAndWaitUntilDeleted(job,
                                                labelSelector,
                                                podProgress,
                                                podCompletion(k8s),
                                                watchContinuouslyRequestTimeout,
                                                deletionMonitorRepeatDelay,
                                                Some(pool))
            }
            .runForeach(_ => ())
    
          // Shutdown the pool, if any.
          _ <- hcp.fold(Future.successful(()))(_.shutdown().map(_ => ()))
    
        } yield ()
    ```

    For a working example, see: [PiJobsSequential.scala](examples/job/PiJobsSequential.scala)
    
- In parallel

    Define a list of job execution futures,
    taking care of shutting down the pool after completion.
    
    ```scala
    
    def metadata(n: Int) =
      ObjectMeta(name = s"pi-$n",
                 labels = Map("job-kind" -> s"piTest$n", "iteration" -> s"$n"))
    def labelSelector(n: Int) =
      LabelSelector(LabelSelector.IsEqualRequirement("job-kind", s"piTest$n"))

    val jobs = Seq.tabulate[Future[Unit]](n = 10) { n =>
      val jname = s"pi-$n"
      val job: Job = if (n % 3 == 0) {
        // simulate a job failure
        val piContainer = Container(name = "pi",
                                    image = "nowhere/does-not-exist:latest",
                                    command = List("/bin/bash"),
                                    args = List("-c", "env"))
        val piSpec = Pod
          .Spec()
          .addContainer(piContainer)
          .withRestartPolicy(RestartPolicy.Never)
        val piTemplateSpec =
          Pod.Template.Spec(metadata = metadata(n)).withPodSpec(piSpec)
        Job(jname).withTemplate(piTemplateSpec)
      } else {
        val piContainer = Container(
          name = "pi",
          image = "perl",
          command =
            List("perl", "-Mbignum=bpi", "-wle", s"print bpi(${n * 10})"))
        val piSpec = Pod
          .Spec()
          .addContainer(piContainer)
          .withRestartPolicy(RestartPolicy.Never)
        val piTemplateSpec =
          Pod.Template.Spec(metadata = metadata(n)).withPodSpec(piSpec)
        Job(jname).withTemplate(piTemplateSpec)
      }

      for {
        // Execute the job with a unique pool
        (_, hcp, _) <- k8s.executeJobAndWaitUntilDeleted(
          job,
          labelSelector(n),
          podProgress,
          podCompletion(k8s),
          watchContinuouslyRequestTimeout,
          deletionMonitorRepeatDelay,
          None)

        // Shutdown the pool, if any.
        _ <- hcp.fold(Future.successful(()))(_.shutdown().map(_ => ()))
      } yield ()

    }
    ```
    
    Execute the job futures in parallel.
    
    ```scala
     val f: Future[Unit] =
      Future.foldLeft[Unit, Unit](jobs)(())((_: Unit, _: Unit) => ())
    ```

    For a working example, see: [PiJobsParallel.scala](examples/job/PiJobsParallel.scala)
    
## Safely shutdown the client

```scala
// Close client.
// This prevents any more requests being sent by the client.
k8s.close

// this closes the connection resources etc.
system.terminate
```