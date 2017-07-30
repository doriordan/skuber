package skuber.api

import mockws.MockWS
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._
import play.api.http.HttpVerbs._
import skuber._
import skuber.api.client.{Context, RequestContext}
import skuber.api.security.HTTPRequestAuth
import skuber.json.format._

import LabelSelector.dsl._

/**
 * @author Chris Baker
 */
class ListQueryParamSpec extends Specification {

  type EE = ExecutionEnv

  "LabelSelector on List is propagated to the API as query parameters" >> { implicit ee: EE =>

    val db   = Service("db")
    val web_blue  = Service("web-blue")
    val web_green = Service("web-green")

    // i don't want to bother with a bunch of code here to implement selection by labels, so I will just fake the returns
    // downside: this is fragile because of tight coupling to the calls below, it makes assumptions on the formatting (e.g., whitespace) and on the ordering of the label selectors in the multi-selector case
    val ws = MockWS {
      case (GET,"http://server/api/v1/namespaces/default/services") => Action{request =>
        request.queryString.get("labelSelector") match {
          case Some(Seq("tier=database"))            => Ok(Json.toJson(ServiceList(items = List(db))))
          case Some(Seq("tier=web"))                 => Ok(Json.toJson(ServiceList(items = List(web_blue, web_green))))
          case Some(Seq("tier!=database"))           => Ok(Json.toJson(ServiceList(items = List(web_blue, web_green))))
          case Some(Seq("tier in (web),stage=blue")) => Ok(Json.toJson(ServiceList(items = List(web_blue))))
          case None                                  => Ok(Json.toJson(ServiceList(items = List(db,web_blue,web_green))))
          case _                                     => BadRequest("test not written for this")
        }
      }
    }

    val sk8 = new RequestContext( ws.url(_), "http://server", HTTPRequestAuth.NoAuth, Namespace.default.name, () => ws.close())

    sk8.list[ServiceList]().map(_.items) must containTheSameElementsAs(List(db,web_blue,web_green)).await

    sk8.list[ServiceList](LabelSelector("tier" is "database")).map(_.items) must containTheSameElementsAs(List(db)).await

    sk8.list[ServiceList](LabelSelector("tier" is "web")).map(_.items) must containTheSameElementsAs(List(web_blue,web_green)).await

    sk8.list[ServiceList](LabelSelector("tier" isNot "database")).map(_.items) must containTheSameElementsAs(List(web_blue,web_green)).await

    sk8.list[ServiceList](LabelSelector("tier" isIn List("web"), "stage" is "blue")).map(_.items) must containTheSameElementsAs(List(web_blue)).await
  }

  "No labelSelector query parameter if LabelSelector not specified to ::list" >> { implicit ee: EE =>

    val ws = MockWS {
      case (GET,"http://server/api/v1/namespaces/default/pods") => Action{request =>
        request.queryString.get("labelSelector") match {
          case None => Ok(Json.toJson(PodList(List())))
          case _ => BadRequest("test not written for this")
        }
      }
    }

    val sk8 = new RequestContext( ws.url(_), "http://server", HTTPRequestAuth.NoAuth, Namespace.default.name, () => ws.close())

    sk8.list[PodList]().map(_.items.isEmpty) must beTrue.await
  }

}