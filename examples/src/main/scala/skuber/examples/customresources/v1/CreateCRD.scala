package skuber.examples.customresources.v1

import org.apache.pekko.actor.ActorSystem
import play.api.libs.json.{JsObject, Json}
import skuber.ResourceSpecification.{Schema, Scope}
import skuber.apiextensions.v1.CustomResourceDefinition
import skuber.{K8SException, ResourceSpecification, k8sInit}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 * @author David O'Riordan, original v1beta1 example ported to V1 by Ash White
 *         Create the Team and ServiceSupport CRDs on k8s
 */
object CreateCRD extends App {

  // As part of the v1 CRD API, a Json Schema needs to be supplied as part of a list of versions for the CRD
  val teamJsonSchema =
    Json.parse(
      """{
        |  "type": "object",
        |  "properties": {
        |    "spec": {
        |      "type": "object",
        |      "properties": {
        |        "teamName": {
        |          "type": "string"
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin
    ).as[JsObject]

  // CRD for the organizations teams, each team should be represented by a single Team resource.
  // A teams resources may in some cases (we assume for demo purposes) exist in multiple namespaces, so scope of Team
  // is Clustered rather than the default of Namespaced
  val teamCrd = CustomResourceDefinition(
    name = "teams.examples.skuber.io",
    kind = "Team",
    scope = Scope.Cluster,
    shortNames = List.empty,
    versions = List(
      ResourceSpecification.Version(
        name = "v1alpha1",
        served = true,
        storage = true,
        schema = Some(Schema(teamJsonSchema))
      )
    )
  )

  val supportJsonSchema =
    Json.parse(
      """{
        |  "type": "object",
        |  "properties": {
        |    "spec": {
        |      "type": "object",
        |      "properties": {
        |        "personOnCall": {
        |          "type": "string"
        |        }
        |      }
        |    }
        |  }
        |}""".stripMargin
    ).as[JsObject]

  // CRD for the organizations service support (SUP) information, each service should have one SUP resource
  // Scope is default i.e. Namespaced - each SUP resource should be in the same namespace as the resources of the
  // corresponding service
  val svcSupportCrd = CustomResourceDefinition(
    name = "servicesupports.examples.skuber.io",
    kind = "ServiceSupport",
    scope = Scope.Namespaced,
    shortNames = "sup" :: Nil,
    versions = List(
      ResourceSpecification.Version(
        name = "v2",
        served = true,
        storage = true,
        schema = Some(Schema(supportJsonSchema))
      )
    )
  )

  implicit val system: ActorSystem = ActorSystem()
  implicit val dispatcher: ExecutionContextExecutor = system.dispatcher

  val k8s = k8sInit

  val saveCRDs = for {
    _ <- save(teamCrd)
    s <- save(svcSupportCrd)
  } yield s

  saveCRDs.onComplete{
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

  def save(crd: CustomResourceDefinition): Future[CustomResourceDefinition] = {
    k8s.create(crd).recoverWith {
      case notFound: K8SException if notFound.status.code.contains(404) => {
        // Probably due to running against pre v1.16 cluster where the v1 CRD API isn't supported
        System.err.println("Unable to create CRD - please check that your k8s cluster is at v1.16 or above")
        throw notFound
      }
      case alreadyExists: K8SException if alreadyExists.status.code.contains(409) =>
        // Update needs to use the current resource version of an existing resource in order to be accepted by k8s
        k8s.get[CustomResourceDefinition](crd.name).flatMap { existing =>
          val currentVersion = existing.metadata.resourceVersion
          val newMeta = crd.metadata.copy(resourceVersion = currentVersion)
          val updatedObj = crd.copy(metadata = newMeta)
          k8s.update(updatedObj)
        }
    }
  }
}
