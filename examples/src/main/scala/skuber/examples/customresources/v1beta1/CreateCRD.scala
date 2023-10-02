package skuber.examples.customresources.v1beta1

import skuber.{K8SException, k8sInit}
import skuber.ResourceSpecification.Scope
import skuber.apiextensions.v1beta1.CustomResourceDefinition
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
  * @author David O'Riordan
  *         Create the Team and ServiceSupport CRDs on k8s
  */
object CreateCRD extends App {

  // CRD for the organizations teams, each team should be represented by a single Team resource.
  // A teams resources may in some cases (we assume for demo purposes) exist in multiple namespaces, so scope of Team
  // is Clustered rather than the default of Namespaced
  val teamCrd = CustomResourceDefinition(name = "teams.examples.skuber.io",
    kind = "Team",
    scope = Scope.Cluster)

  // CRD for the organizations service support (SUP) information, each service should have one SUP resource
  // Scope is default i.e. Namespaced - each SUP resource should be in the same namespace as the resources of the
  // corresponding service
  val svcSupportCrd = CustomResourceDefinition(name = "servicesupports.examples.skuber.io",
    kind = "ServiceSupport",
    shortNames = "sup" :: Nil)

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val k8s = k8sInit

  val saveCRDs = for {
    _ <- save(teamCrd)
    s <- save(svcSupportCrd)
  } yield s

  saveCRDs onComplete {
    case Success(_) =>
      System.out.println("done!")
      k8s.close
      system.terminate().foreach { f =>
        System.exit(0)
      }
    case Failure(ex) =>
      System.err.println("Failed: " + ex)
      k8s.close
      system.terminate().foreach { f =>
        System.exit(1)
      }
  }

  def save(crd: CustomResourceDefinition) = {
    k8s create (crd) recoverWith {
      case notFound: K8SException if notFound.status.code.contains(404) => {
        // probably due to running against pre v1.7 cluster where CRDs don't exist as a resource kind
        System.err.println("Unable to create CRD - please check that your k8s cluster is at v1.7 or above")
        throw notFound
      }
      case alreadyExists: K8SException if alreadyExists.status.code.contains(409) =>
        // update needs to use the rcurrent resource version of existing resource in order to be accepted by k8s
        k8s.get[CustomResourceDefinition] (crd.name) flatMap { existing =>
          val currentVersion = existing.metadata.resourceVersion
          val newMeta = crd.metadata.copy(resourceVersion = currentVersion)
          val updatedObj = crd.copy(metadata = newMeta)
          k8s update (updatedObj)
        }
    }
  }
}
