package skuber.api

import org.specs2.mutable.Specification

import skuber.model.LabelSelector
import LabelSelector.dsl._

import scala.language.{postfixOps, reflectiveCalls}

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
