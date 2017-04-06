package skuber

import skuber.api.client.{Kind, ListKind, ObjKind}
import skuber.json.apps.format._

/**
  * The skuber.apps package contains classes and methods for supporting the Kubernetes Apps Group API.
  * This currently (Kubernetes V1.6) includes StatefulSet
  * resource type.
  *
  * Note that types in this group are not (yet) officially supported on Kubernetes, and so may be changed or removed in
  * future versions. Thus the 'skuber.apps' package might well be subject to more backwards-incompatible changes than
  * the main 'skuber' package, although both Skuber packages still have alpha release status.
  * Support for some of these types may not be enabled by default on the Kubernetes API server - see Kubernetes docs
  * for instructions on enabling them if necessary.
  *
  * @author Hollin Wilkins
  */
package object apps {
  val appsAPIVersion = "apps/v1beta1"

  trait IsAppsKindId[T <: TypeMeta] { self: Kind[T] =>
    override def isAppsKind = true
  }

  implicit val statefulSetKind: ObjKind[StatefulSet] = new ObjKind[StatefulSet]("statefulsets", "StatefulSet") with IsAppsKindId[StatefulSet]

  case class StatefulSetList(val kind: String = "StatefulSetList",
                             override val apiVersion: String = appsAPIVersion,
                             val metadata: Option[ListMeta] = None,
                             items: List[StatefulSet] = Nil) extends KList[StatefulSet]
  implicit val statefulSetListKind = new ListKind[StatefulSetList]("statefulsets")
    with IsAppsKindId[StatefulSetList]
}
