package skuber.policy.v1beta1

import skuber.ResourceSpecification.{Names, Scope}
import skuber.{IntOrString, LabelSelector, NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition, Scale, Timestamp}

case class PodDisruptionBudget(override val kind: String = "PodDisruptionBudget",
                               override val apiVersion: String = policyAPIVersion,
                               metadata: ObjectMeta,
                               spec: Option[PodDisruptionBudget.Spec] = None,
                               status: Option[PodDisruptionBudget.Status] = None) extends ObjectResource {

  private lazy val copySpec: PodDisruptionBudget.Spec = this.spec.getOrElse(PodDisruptionBudget.Spec(selector=Some(LabelSelector())))

  def withLabelSelector(sel: LabelSelector): PodDisruptionBudget = {
    this.copy(spec = Some(copySpec.copy(selector = Some(sel))))
  }

  def withMaxUnavailable(value: IntOrString): PodDisruptionBudget = {
    this.copy(spec = Some(copySpec.copy(maxUnavailable = Some(value))))
  }

  def withMinAvailable(value: IntOrString): PodDisruptionBudget = {
    this.copy(spec = Some(copySpec.copy(minAvailable = Some(value))))
  }
}

object PodDisruptionBudget {

  def apply(name: String): PodDisruptionBudget = {
    PodDisruptionBudget(metadata = ObjectMeta(name = name))
  }

  val specification = NonCoreResourceSpecification(apiGroup = "policy",
    version = "v1beta1",
    scope = Scope.Namespaced,
    names = Names(plural = "poddisruptionbudgets",
      singular = "poddisruptionbudget",
      kind = "PodDisruptionBudget",
      shortNames = List("pdb")))
  implicit val stsDef: ResourceDefinition[PodDisruptionBudget] = new ResourceDefinition[PodDisruptionBudget] {
    def spec: NonCoreResourceSpecification = specification
  }
  implicit val stsListDef: ResourceDefinition[PodDisruptionBudgetList] = new ResourceDefinition[PodDisruptionBudgetList] {
    def spec: NonCoreResourceSpecification = specification
  }

  case class Spec(maxUnavailable: Option[IntOrString] = None,
                  minAvailable: Option[IntOrString] = None,
                  selector: Option[LabelSelector] = None)

  case class Status(currentHealthy: Int,
                    desiredHealthy: Int,
                    disruptedPods: Map[String, Timestamp],
                    disruptionsAllowed: Int,
                    expectedPods: Int,
                    observedGeneration: Option[Int])

  import play.api.libs.functional.syntax._
  import play.api.libs.json.{Format, JsPath}
  import skuber.json.format._

  implicit val depStatusFmt: Format[Status] = ((JsPath \ "currentHealthy").formatMaybeEmptyInt() and
      (JsPath \ "desiredHealthy").formatMaybeEmptyInt() and
      (JsPath \ "disruptedPods").formatMaybeEmptyMap[Timestamp] and
      (JsPath \ "disruptionsAllowed").formatMaybeEmptyInt() and
      (JsPath \ "expectedPods").formatMaybeEmptyInt() and
      (JsPath \ "observedGeneration").formatNullable[Int]) (Status.apply, s => (s.currentHealthy, s.desiredHealthy, s.disruptedPods, s.disruptionsAllowed, s.expectedPods, s.observedGeneration))

  implicit val depSpecFmt: Format[Spec] = ((JsPath \ "maxUnavailable").formatNullable[IntOrString] and
      (JsPath \ "minAvailable").formatNullable[IntOrString] and
      (JsPath \ "selector").formatNullableLabelSelector) (Spec.apply, s => (s.maxUnavailable, s.minAvailable, s.selector))

  implicit lazy val pdbFormat: Format[PodDisruptionBudget] = (objFormat and
      (JsPath \ "spec").formatNullable[Spec] and
      (JsPath \ "status").formatNullable[Status])(PodDisruptionBudget.apply, p => (p.kind, p.apiVersion, p.metadata, p.spec, p.status))

  implicit val pdbListFormat: Format[PodDisruptionBudgetList] = ListResourceFormat[PodDisruptionBudget]
}