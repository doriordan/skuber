package skuber.examples.job

import akka.actor.ActorSystem
import skuber.{Container, Pod, RestartPolicy, k8sInit}
import skuber.batch.Job
import skuber.json.batch.format._
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
 * @author David O'Riordan
 * Simple Job example
 * Adapted from the example at https://kubernetes.io/docs/user-guide/jobs/#running-an-example-job
 * Starts a Job on Kubernetes that prints pi to 2000 decimal places
 */
object PrintPiJob extends App {


  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val k8s = k8sInit

  val piContainer = Container(name="pi", image="perl", command = List("perl", "-Mbignum=bpi", "-wle","print bpi(2000)"))
  val piSpec = Pod.Spec().addContainer(piContainer).withRestartPolicy(RestartPolicy.Never)
  val piTemplateSpec = Pod.Template.Spec.named("pi").withPodSpec(piSpec)
  val piJob = Job("pi").withTemplate(piTemplateSpec)

  // kick off the job on the cluster
  val jobCreateFut = k8s create piJob
  jobCreateFut onComplete {
    case Success(job) =>
      System.out.println("Job successfully created on the cluster")
      k8s.close
      system.terminate().foreach { f =>
        System.exit(0)
      }
    case Failure(ex) =>
      System.err.println("Failed to create job: " + ex)
      k8s.close
      system.terminate().foreach { f =>
        System.exit(1)
      }
  }
  // The job can be tracked using 'kubectl get pods' to get the name of the pod running the job (starts with "pi-")
  // and then when the pod terminates use "kubectl logs <pod name>" to see the printed pi result
}