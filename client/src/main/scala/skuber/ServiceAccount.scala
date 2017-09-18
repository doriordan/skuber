package skuber

/**
 * @author David O'Riordan
 */
case class ServiceAccount(
    val kind: String ="ServiceAccount",
    override val apiVersion: String = "v1",
    val metadata: ObjectMeta,
    secrets: List[ObjectReference] = List(),
    imagePullSecrets: List[LocalObjectReference] = List())
  extends ObjectResource {
  
  def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))
}

object ServiceAccount {

  val specification=CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "serviceaccounts",
      singular = "serviceaccount",
      kind = "ServiceAccount",
      shortNames = List("sa")
    )
  )
  implicit val saDef = new ResourceDefinition[ServiceAccount] { def spec=specification }
  implicit val saListDef = new ResourceDefinition[ServiceAccountList] { def spec=specification }
}