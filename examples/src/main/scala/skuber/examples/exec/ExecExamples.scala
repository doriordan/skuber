package skuber.examples.exec

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import skuber._
import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}
import scala.concurrent.duration.Duration.Inf
import skuber.json.format._

object ExecExamples extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val k8s = k8sInit

  println("Executing commands in pods ==>")

  val podName = "sleep"
  val containerName = "sleep"
  val sleepContainer = Container(name = containerName, image = "busybox", command = List("sh", "-c", "trap exit TERM; sleep 99999 & wait"))
  val sleepPod = Pod(podName, Pod.Spec().addContainer(sleepContainer))

  val terminalReady: Promise[Unit] = Promise()

  // Just print stdout and signal when the terminal gets ready
  val sink: Sink[String, Future[Done]] = Sink.foreach {
    case s =>
      print(s)
      if (s.startsWith("/ #")) {
        terminalReady.success(())
      }
  }

  // Execute `ps aux` when the terminal gets ready
  val source: Source[String, NotUsed] = Source.future(terminalReady.future.map { _ =>
    "ps aux\n"
  })

  // Wait for a while to ensure outputs
  def close: Promise[Unit] = {
    val promise = Promise[Unit]()
    Future {
      Thread.sleep(1000)
      promise.success(())
    }
    promise
  }

  val fut = for {
    // Create the sleep pod if not present
    _ <- k8s.getOption[Pod](podName).flatMap {
      case Some(pod) => Future.successful(pod)
      case None =>
        k8s.create(sleepPod).map { _ =>
          Thread.sleep(3000)
        }
    }
    // Simulate kubectl exec
    _ <- {
      println("`kubectl exec ps aux`")
      k8s.exec(podName, Seq("ps", "aux"), maybeStdout = Some(sink), maybeClose = Some(close))
    }
    // Simulate kubectl exec -it
    _ <- {
      println("`kubectl -it exec sh` -> `ps aux`")
      k8s.exec(podName, Seq("sh"), maybeStdout = Some(sink), maybeStdin = Some(source), tty = true, maybeClose = Some(close))
    }
  } yield ()

  // Clean up
  fut.onComplete { _ =>
    println("\nFinishing up")
    k8s.delete[Pod]("sleep")
    k8s.close
    system.terminate().foreach { f =>
      System.exit(0)
    }
  }

  Await.result(fut, Inf)
}
