package skuber.examples.job

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}
import skuber.api.client.{
  EventType,
  KubernetesClient,
  WatchEvent,
  defaultK8sConfig
}
import skuber.batch.Job
import skuber.json.batch.format._
import skuber.json.format._
import skuber.{
  Container,
  LabelSelector,
  ObjectMeta,
  Pod,
  RestartPolicy,
  k8sInit
}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Demonstrates three things:
  * 1) executing jobs sequentially, creating a pool for the first job and reusing it afterwards
  * 2) watching continuously pod events until any container status or pod status indicates a non-progress condition.
  * 3) making sure that the host connection pool used for watching is shutdown
  */
object PiJobsSequential {

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

  def durationFomConfig(config: Config)(configKey: String): Option[Duration] =
    Some(Duration.fromNanos(config.getDuration(configKey).toNanos))

  def getSkuberConfig[T](config: Config,
                         key: String,
                         fromConfig: String => Option[T],
                         default: T): T = {
    val skuberConfigKey = s"skuber.$key"
    if (config.getIsNull(skuberConfigKey)) {
      default
    } else {
      fromConfig(skuberConfigKey) match {
        case None    => default
        case Some(t) => t
      }
    }
  }

  def podCompletion(k8s: KubernetesClient)(lastPodEvent: WatchEvent[Pod])(
      implicit ec: ExecutionContext,
      mat: ActorMaterializer): Future[Unit] = {

    def printLogFlow(cntrName: String): Sink[ByteString, NotUsed] =
      Flow[ByteString]
        .via(
          Framing.delimiter(ByteString("\n"),
                            maximumFrameLength = 10000,
                            allowTruncation = true))
        .map(_.utf8String)
        .to(Sink.foreach(text => println(s"[$cntrName logs] $text")))

    def showContainerStateIfSuccessful(cs: Container.Status,
                                       podName: String,
                                       message: String): Future[Unit] =
      cs.state.fold[Future[Unit]](Future.successful(())) {
        case s: Container.Terminated if s.exitCode == 0 =>
          for {
            logSource <- k8s.getPodLogSource(
              name = podName,
              queryParams = Pod.LogQueryParams(containerName = Some(cs.name)))
            _ = logSource.runWith(printLogFlow(message))
          } yield ()
        case s =>
          println(s"[$message] No logs because of unsuccessful status: $s")
          Future.successful(())
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

  def main(
      args: Array[String]
  ): Unit = {

    implicit val as: ActorSystem = ActorSystem("PiJobsSequential")
    implicit val ec: ExecutionContext = as.dispatcher
    implicit val mat: ActorMaterializer = ActorMaterializer()
    val sconfig: skuber.api.Configuration = defaultK8sConfig
    val aconfig: Config = ConfigFactory.load()
    implicit val k8s: KubernetesClient =
      k8sInit(config = sconfig, appConfig = aconfig)

    val watchContinuouslyRequestTimeout: Duration = getSkuberConfig(
      aconfig,
      "watch-continuously.request-timeout",
      durationFomConfig(aconfig),
      30.seconds)

    val deletionMonitorRepeatDelay: FiniteDuration = 1.second

    def metadata(n: Int) =
      ObjectMeta(name = "pi",
                 labels = Map("job-kind" -> "piTest", "iteration" -> s"$n"))
    val labelSelector = LabelSelector(
      LabelSelector.IsEqualRequirement("job-kind", "piTest"))

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

    // Wait until done and shutdown k8s & akka.
    Await.result(f.flatMap { _ =>
      k8s.close
      as.terminate().map(_ => ())
    }, Duration.Inf)

  }
}
