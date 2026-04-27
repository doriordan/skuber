package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class EndpointsApplyConfig(
  kind: String = "Endpoints",
  apiVersion: String = "v1",
  metadata: Option[ObjectMetaApplyConfig] = None,
  subsets: Option[List[Endpoints.Subset]] = None
) extends ApplyConfiguration[Endpoints] {
  def name: String = metadata.flatMap(_.name).getOrElse("")
  def withMetadata(m: ObjectMetaApplyConfig): EndpointsApplyConfig = copy(metadata = Some(m))
  def addLabel(kv: (String, String)): EndpointsApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addLabel(kv)))
  def addAnnotation(kv: (String, String)): EndpointsApplyConfig = copy(metadata = Some(metadata.getOrElse(ObjectMetaApplyConfig()).addAnnotation(kv)))
  def addSubset(s: Endpoints.Subset): EndpointsApplyConfig = copy(subsets = Some(s :: subsets.getOrElse(Nil)))
}

object EndpointsApplyConfig {
  def apply(name: String): EndpointsApplyConfig =
    EndpointsApplyConfig(metadata = Some(ObjectMetaApplyConfig(name = Some(name))))

  implicit val writes: OWrites[EndpointsApplyConfig] = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").writeNullable[ObjectMetaApplyConfig] and
    (JsPath \ "subsets").writeNullable[List[Endpoints.Subset]]
  )(e => (e.kind, e.apiVersion, e.metadata, e.subsets))
}
