package skuber.api

import mockws.MockWS
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.http.HttpVerbs._
import play.api.mvc.Results._
import play.api.mvc._
import play.api.mvc.BodyParsers.parse
import skuber._
import skuber.api.client.RequestContext
import skuber.api.security.HTTPRequestAuth
import skuber.ext.Deployment
import skuber.json.ext.format._

/**
 * @author Chris Baker
 */
class PartiallyUpdateWithPatchSpec extends Specification {

  type EE = ExecutionEnv

  "Patch should pass the minimal payload" >> { implicit ee: EE =>

    val ws = MockWS {
      case (PATCH,"http://server/apis/extensions/v1beta1/namespaces/default/deployments/test-depl") => Action(parse.json){ request =>
        // TODO: not sure what to test here
        println(request.body)
        Ok(request.body)
      }
    }

    val sk8 = new RequestContext( ws.url(_), "http://server", HTTPRequestAuth.NoAuth, Namespace.default.name, () => ws.close())

    // TODO: not sure what to test here
    sk8.partiallyUpdate[Deployment](Deployment("test-depl").withReplicas(8)).map(_.spec.map(_.replicas)) must beSome(8).await
  }

}