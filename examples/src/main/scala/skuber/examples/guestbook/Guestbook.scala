package skuber.examples.guestbook

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
  
/**
 * @author David O'Riordan
 */
object Guestbook extends App {
  val sys = ActorSystem("SkuberExamples")
  val guestbook = sys.actorOf(Props[GuestbookActor], "guestbook")
  
  implicit val timeout = Timeout(40 seconds)
  
  val deploymentResult = ask(guestbook, GuestbookActor.Deploy)
  deploymentResult map { result =>
    result match {
      case GuestbookActor.DeployedSuccessfully => {
        System.out.println("\n*** Deployment of Guestbook application to Kubernetes completed successfully!")
        System.out.println("Waiting 5 seconds to allow skuber client to close...")
        Thread.sleep(5000)
        System.exit(0)
      }
      case GuestbookActor.DeploymentFailed(ex) => {
        System.err.println("\n!!! Deployment of Guestbook application failed: " + ex)
        System.out.println("Waiting 5 seconds to allow skuber client to close...")
        Thread.sleep(5000)
        System.exit(1)
      }
    }  
  }
  deploymentResult onFailure { 
    case ex => System.err.println("Unexpected error deploying Guestbook: " + ex)
    System.exit(-1)
  }
}
