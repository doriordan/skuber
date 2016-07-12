package skuber.ext

import skuber.{LabelSelector, ObjectMeta, ObjectResource, Pod}

/**
  * @author Cory Klein
  */
case class DaemonSet(val kind: String ="Secret",
                     override val apiVersion: String = extensionsAPIVersion,
                     val metadata: ObjectMeta,
                     spec:  Option[DaemonSet.Spec] = None,
                     status:  Option[DaemonSet.Status] = None)
  extends ObjectResource {
}

object DaemonSet {
  case class Spec(selector: Option[LabelSelector] = None,
                  template: Option[Pod.Template.Spec] = None)

  case class Status(currentNumberScheduled: Int = 0,
                    numberMisscheduled: Int = 0,
                    desiredNumberScheduled: Int = 0)
}
