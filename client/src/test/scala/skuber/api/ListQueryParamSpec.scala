package skuber.api

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import skuber._

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

  type EE = ExecutionEnv

  "LabelSelector string representations equate to the associated labelSelector query param values" >> { implicit ee: EE =>

    LabelSelector("tier" is "database").toString mustEqual "tier=database"
    LabelSelector("tier" isNot "database").toString mustEqual "tier!=database"
    LabelSelector("tier" isIn List("web","database")).toString mustEqual "tier in (web,database)"
    LabelSelector("tier" isIn List("database","app"), "version" is "1").toString mustEqual "tier in (database,app),version=1"
  }
}