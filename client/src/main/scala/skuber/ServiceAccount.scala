package skuber

/**
 * @author David O'Riordan
 */
case class ServiceAccount(val kind: String = "ServiceAccount",
                          override val apiVersion: String = "v1",
                          val metadata: ObjectMeta,
                          secrets: List[ObjectReference] = List(),
                          imagePullSecrets: List[LocalObjectReference] = List(),
                          automountServiceAccountToken: Option[Boolean] = None)
  extends ObjectResource {

  def withResourceVersion(version: String): ServiceAccount = this.copy(metadata = metadata.copy(resourceVersion = version))
}

object ServiceAccount {

  val specification: CoreResourceSpecification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural = "serviceaccounts",
      singular = "serviceaccount",
      kind = "ServiceAccount",
      shortNames = List("sa")))
  implicit val saDef: ResourceDefinition[ServiceAccount] = new ResourceDefinition[ServiceAccount] {
    def spec: ResourceSpecification = specification
  }
  implicit val saListDef: ResourceDefinition[ServiceAccountList] = new ResourceDefinition[ServiceAccountList] {
    def spec: ResourceSpecification = specification
  }
}
