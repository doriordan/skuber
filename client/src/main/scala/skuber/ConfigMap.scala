package skuber

/**
  * @author Cory Klein
  */
case class ConfigMap(val kind: String ="ConfigMap",
                     override val apiVersion: String = v1,
                     val metadata: ObjectMeta,
                     data: Map[String, String] = Map())
  extends ObjectResource

