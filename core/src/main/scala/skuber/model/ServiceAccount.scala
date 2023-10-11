package skuber.model

/**
 * @author David O'Riordan
 */
case class ServiceAccount(
    kind: String ="ServiceAccount",
    apiVersion: String = "v1",
    metadata: ObjectMeta,
    secrets: List[ObjectReference] = List(),
    imagePullSecrets: List[LocalObjectReference] = List())
  extends ObjectResource
{
  
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
  implicit val saDef: ResourceDefinition[ServiceAccount] = new ResourceDefinition[ServiceAccount] { def spec=specification }
  implicit val saListDef: ResourceDefinition[ServiceAccountList] = new ResourceDefinition[ServiceAccountList] { def spec=specification }
}