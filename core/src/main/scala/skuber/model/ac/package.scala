package skuber.model

import play.api.libs.json._
import play.api.libs.functional.syntax._

package object ac {

  trait ApplyConfiguration[O <: ObjectResource] {
    def name: String
  }

  case class ObjectMetaApplyConfig(
    name: Option[String] = None,
    namespace: Option[String] = None,
    labels: Option[Map[String, String]] = None,
    annotations: Option[Map[String, String]] = None,
    finalizers: Option[List[String]] = None
  ) {
    def withName(n: String): ObjectMetaApplyConfig = copy(name = Some(n))
    def withNamespace(ns: String): ObjectMetaApplyConfig = copy(namespace = Some(ns))
    def withLabels(l: Map[String, String]): ObjectMetaApplyConfig = copy(labels = Some(l))
    def addLabel(kv: (String, String)): ObjectMetaApplyConfig = copy(labels = Some(labels.getOrElse(Map.empty) + kv))
    def withAnnotations(a: Map[String, String]): ObjectMetaApplyConfig = copy(annotations = Some(a))
    def addAnnotation(kv: (String, String)): ObjectMetaApplyConfig = copy(annotations = Some(annotations.getOrElse(Map.empty) + kv))
  }

  object ObjectMetaApplyConfig {
    implicit val writes: OWrites[ObjectMetaApplyConfig] = (
      (JsPath \ "name").writeNullable[String] and
      (JsPath \ "namespace").writeNullable[String] and
      (JsPath \ "labels").writeNullable[Map[String, String]] and
      (JsPath \ "annotations").writeNullable[Map[String, String]] and
      (JsPath \ "finalizers").writeNullable[List[String]]
    )(o => (o.name, o.namespace, o.labels, o.annotations, o.finalizers))
  }
}
