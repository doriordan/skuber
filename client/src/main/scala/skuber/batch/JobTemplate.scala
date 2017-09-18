package skuber.batch

import skuber.{ObjectMeta, ObjectResource}
/**
  * @author David O'Riordan
  */

object JobTemplate {

  case class Spec(spec: Job.Spec, metadata: Option[ObjectMeta] = None)

}
