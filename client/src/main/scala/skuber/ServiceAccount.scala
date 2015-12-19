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
  extends ObjectResource 