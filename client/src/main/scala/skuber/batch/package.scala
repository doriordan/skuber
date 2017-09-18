package skuber

import scala.language.implicitConversions

import skuber.json.batch.format._
import skuber.api.client._

/**
  * Created by Cory Klein on 9/30/16.
  */
package object batch {
  val batchAPIVersion = "batch/v1"
  type JobList = ListResource[Job]
  type CronJobList = ListResource[CronJob]
}
