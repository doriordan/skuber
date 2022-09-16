package skuber.network.v1

import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber._
import skuber.networking.v1.Ingress
import skuber.networking.v1.Ingress.PathType._


class IngressSpec extends Specification {
  "This is a unit specification for the skuber Ingress class from v1 api version. ".txt

  "An Ingress object can be written to Json and then read back again successfully" >> {
    val ingress = Ingress("example")
      .addHttpRule("example.com", Exact, Map(
        "/" -> "service:80",
        "/about" -> "another-service:http"
      ))

    val readIng = Json.fromJson[Ingress](Json.toJson(ingress)).get
    readIng mustEqual ingress
  }

  "An Ingress object with empty Path can be read directly from a JSON string" >> {
    val ingJsonStr =
      """
        |{
        |  "apiVersion": "networking.k8s.io/v1",
        |  "kind": "Ingress",
        |  "metadata": {
        |    "creationTimestamp": "2017-04-02T19:39:34Z",
        |    "generation": 3,
        |    "labels": {
        |      "app": "ingress"
        |    },
        |    "name": "example-ingress",
        |    "namespace": "default",
        |    "resourceVersion": "1313499",
        |    "selfLink": "/apis/extensions/v1/namespaces/default/ingresses/example",
        |    "uid": "192dd131-17dc-11e7-bd9c-0a5e79684354"
        |  },
        |  "spec": {
        |    "rules": [
        |      {
        |        "host": "example.com",
        |        "http": {
        |          "paths": [
        |            {
        |              "backend": {
        |                "service": {
        |                  "name": "example-svc",
        |                  "port": {
        |                    "number": 8080
        |                  }
        |                },
        |                "pathType": "Exact"
        |              }
        |            }
        |          ]
        |        }
        |      }
        |    ],
        |    "tls": [
        |      {
        |        "hosts": ["abc","def"]
        |      }
        |    ],
        |    "ingressClassName": "nginx"
        |  },
        |  "status": {
        |    "loadBalancer": {
        |      "ingress": [
        |        {
        |          "hostname": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-1111111111.us-east-1.elb.amazonaws.com"
        |        }
        |      ]
        |    }
        |  }
        |}""".stripMargin

    val ing = Json.parse(ingJsonStr).as[Ingress]
    ing.kind mustEqual "Ingress"
    ing.name mustEqual "example-ingress"

    ing.spec.get.rules.head.host must beSome("example.com")
    ing.spec.get.rules.head.http.paths must_== List(
      Ingress.Path(path = "", backend = Ingress.Backend(Some(Ingress.ServiceType("example-svc", Ingress.Port(number = Some(8080)))))),
    )
    ing.spec.get.tls must_== List(Ingress.TLS(
      hosts = List("abc", "def"),
      secretName = None
    ))

  }
}
