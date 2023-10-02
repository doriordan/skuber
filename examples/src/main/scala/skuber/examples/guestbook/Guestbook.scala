package skuber.examples.guestbook

import org.apache.pekko.actor._
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
  
/**
 * @author David O'Riordan
 */
object Guestbook extends App {
  val sys = ActorSystem("SkuberExamples")
  val guestbook = sys.actorOf(Props[GuestbookActor](), "guestbook")
  
  implicit val timeout: Timeout = Timeout(40.seconds)
  
  val deploymentResult = ask(guestbook, GuestbookActor.Deploy)
  deploymentResult map { result =>
    result match {
      case GuestbookActor.DeployedSuccessfully => {
        System.out.println("\n*** Deployment of Guestbook application to Kubernetes completed successfully!")
        sys.terminate().foreach { f =>
          System.exit(0)
        }
      }
      case GuestbookActor.DeploymentFailed(ex) => {
        System.err.println("\n!!! Deployment of Guestbook application failed: " + ex)
        sys.terminate().foreach { f =>
          System.exit(0)
        }
      }
    }  
  }
  deploymentResult.failed.foreach {
    case ex =>
      System.err.println("Unexpected error deploying Guestbook: " + ex)
      sys.terminate().foreach { f =>
        System.exit(1)
      }
  }
}
