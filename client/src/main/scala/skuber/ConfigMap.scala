package skuber

/**
  * @author Cory Klein
  */


case class ConfigMap(val kind: String ="ConfigMap",
                     override val apiVersion: String = v1,
                     val metadata: ObjectMeta,
                     data: Map[String, String] = Map()) extends ObjectResource {

  def withData(data: Map[String, String]): ConfigMap =
    this.copy(data = data)

}

object ConfigMap {
  val specification = CoreResourceSpecification(scope = ResourceSpecification.Scope.Namespaced,
    names = ResourceSpecification.Names(plural="configmaps",
      singular="configmap",
      kind="ConfigMap",
      shortNames=List("cm")))

  implicit val configMapDef: ResourceDefinition[ConfigMap] = new ResourceDefinition[ConfigMap] { def spec=specification }
  implicit val configMapListDef: ResourceDefinition[ConfigMapList] = new ResourceDefinition[ConfigMapList] { def spec=specification }

  def apply(name: String): ConfigMap = new ConfigMap(metadata=ObjectMeta(name=name))

}

