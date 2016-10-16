package skuber

import scala.language.implicitConversions

import skuber.json.batch.format._
import skuber.api.client._

/**
  * Created by Cory Klein on 9/30/16.
  */
package object batch {
  val batchAPIVersion = "batch/v1"


  trait IsBatchKind[T <: TypeMeta] { self: Kind[T] =>
    override def isBatchKind = true
  }

  implicit val jobKind: ObjKind[Job] = new ObjKind[Job]("jobs","Job") with IsBatchKind[Job]

  // support for the corresponding List kinds
  case class JobList( val kind: String = "JobList",
                      override val apiVersion: String = batchAPIVersion,
                      val metadata: Option[ListMeta] = None,
                      items: List[Job] = Nil) extends KList[Job]
  implicit val jobListKind = new ListKind[JobList]("jobs")
    with IsBatchKind[JobList]
}
