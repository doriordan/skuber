package skuber

import akka.actor.{ActorSystem, Scheduler}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.concurrent.duration._
import scala.util.control.NoStackTrace
import org.scalatest.concurrent.ScalaFutures.PatienceConfig

object FutureUtil {

  implicit class FutureOps[T](value: => Future[T]) {

    implicit val patienceConfig: PatienceConfig = PatienceConfig(10.second)

    def valueT(implicit executionContext: ExecutionContext, akkaActor: ActorSystem): T = value.withTimeout().futureValue

    def withTimeout(timeout: FiniteDuration = 10.seconds,
                    cleanup: Option[T => Unit] = None)
                   (implicit executionContext: ExecutionContext, akkaActor: ActorSystem): Future[T] =
      futureTimeout(akkaActor.scheduler, timeout, cleanup)(value)

    def timeoutException(timeout: FiniteDuration) = new TimeoutException(s"Future timed out after ${timeout.toString()}") with NoStackTrace

    /**
     * A function that adds a timeout for a future.
     * The function will return a promise that after a the scheduled timeout will fail the future,
     * if it wasn't completed before.
     * Adds the option for a cleanup callback that will be called if the timeout was reached.
     */
    def futureTimeout[T](scheduler: Scheduler, timeout: FiniteDuration, cleanup: Option[T => Unit] = None)
                        (body: => Future[T])
                        (implicit executionContext: ExecutionContext): Future[T] = {

      if (timeout == Duration.Zero) body
      else {
        val promise = Promise[T]()

        val cancellable = scheduler.scheduleOnce(timeout) {
          promise.completeWith(Future.failed(timeoutException(timeout)))
          cleanup.foreach(f => body.foreach(t => f(t)))
        }

        body.onComplete { result ⇒
          promise.tryComplete(result)
          cancellable.cancel()
        }

        promise.future
      }
    }


  }

}
