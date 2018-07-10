package skuber

import skuber.apiextensions.CustomResourceDefinition
import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import play.api.libs.json._

import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
  * @author David O'Riordan
  */
class CustomResourceSpec extends K8SFixture with Eventually with Matchers {

    val testResourceName: String = java.util.UUID.randomUUID().toString

    case class TestSpec(desiredReplicas: Int)
    case class TestStatus(actualReplicas: Int)

    type TestResource=CustomResource[TestSpec,TestStatus]

    implicit val specFmt:Format[TestSpec] =Json.format[TestSpec]
    implicit val statusFmt:Format[TestStatus] =Json.format[TestStatus]

    implicit val testResourceDefinition = ResourceDefinition[TestResource](
      group = "test.skuber.io",
      version = "v1alpha1",
      kind = "SkuberTest",
      shortNames = List("skt", "skts")
    )

    val testCrd=CustomResourceDefinition[TestResource]

    behavior of "CustomResource"

    it should "create a crd" in { k8s =>

      k8s.create(testCrd) map { c =>
        assert(c.name == testCrd.name)
        assert(c.spec.defaultVersion == "v1alpha1")
        assert(c.spec.group == Some(("test.skuber.io")))
      }
    }

    it should "get the newly created crd" in { k8s =>
      k8s.get[CustomResourceDefinition](testCrd.name) map { c =>
        assert(c.name == testCrd.name)
      }
    }

    it should "create a new custom resource defined by the crd" in { k8s =>
      val testSpec=TestSpec(1)
      val cr=CustomResource(testSpec).withName(testResourceName)
      k8s.create(cr).map { c =>
        assert(c.name==testResourceName)
      }
    }

    it should "get the custom resource" in { k8s =>
      k8s.get[TestResource](testResourceName).map { c =>
        assert(c.name == testResourceName)
        assert(c.spec.desiredReplicas == 1)
        assert(c.status == None)
      }
    }

    it should "delete the custom resource" in { k8s =>
      k8s.delete[TestResource](testResourceName)
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val retrieveCr= k8s.get[TestResource](testResourceName)
        val crRetrieved=Await.ready(retrieveCr, 2 seconds).value.get
        crRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }

    it should "delete the crd" in { k8s =>
      k8s.delete[CustomResourceDefinition](testCrd.name)
      eventually(timeout(200 seconds), interval(3 seconds)) {
        val retrieveCrd= k8s.get[CustomResourceDefinition](testCrd.name)
        val crdRetrieved=Await.ready(retrieveCrd, 2 seconds).value.get
        crdRetrieved match {
          case s: Success[_] => assert(false)
          case Failure(ex) => ex match {
            case ex: K8SException if ex.status.code.contains(404) => assert(true)
            case _ => assert(false)
          }
        }
      }
    }

}
