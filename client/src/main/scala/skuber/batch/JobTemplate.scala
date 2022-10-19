package skuber.batch

import skuber.ObjectMeta
/**
  * @author David O'Riordan
  */

object JobTemplate {

  case class Spec(spec: Job.Spec, metadata: Option[ObjectMeta] = None)

}
