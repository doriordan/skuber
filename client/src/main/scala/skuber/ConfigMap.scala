package skuber

/**
  * @author Cory Klein
  */
case class ConfigMap(
    kind: String = "ConfigMap",
    override val apiVersion: String = v1,
    metadata: ObjectMeta,
    data: Map[String, String] = Map()
) extends ObjectResource

object ConfigMap {
  val specification = CoreResourceSpecification(
    scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(
      plural = "configmaps",
      singular = "configmap",
      kind = "ConfigMap",
      shortNames = List("cm")
    )
  )

  implicit val configMapDef: ResourceDefinition[ConfigMap] = new ResourceDefinition[ConfigMap] {
    def spec: CoreResourceSpecification = specification
  }
  implicit val configMapListDef: ResourceDefinition[ConfigMapList] = new ResourceDefinition[ConfigMapList] {
    def spec: CoreResourceSpecification = specification
  }
}
