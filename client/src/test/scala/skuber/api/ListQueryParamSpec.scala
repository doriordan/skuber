package skuber.api

import org.specs2.mutable.Specification
import skuber._
import scala.language.reflectiveCalls

/**
  * NOTE: This was MockWS based, but not any more due to:
  * - unexplained failures suddenly started to happen when running on Travis CI (but not locally)
  * - skuber 2.0 has migrated away from Play WS client
*/

import LabelSelector.dsl._

/**
 * @author Chris Baker
 * @author David O'Riordan
 */
class ListQueryParamSpec extends Specification {

  "LabelSelector string representations equate to the associated labelSelector query param values" >> {
    LabelSelector("tier" is "database").toString mustEqual "tier=database"
    LabelSelector("tier" isNot "database").toString mustEqual "tier!=database"
    LabelSelector("tier" isIn List("web","database")).toString mustEqual "tier in (web,database)"
    LabelSelector("tier" isIn List("database","app"), "version" is "1").toString mustEqual "tier in (database,app),version=1"
  }
}
