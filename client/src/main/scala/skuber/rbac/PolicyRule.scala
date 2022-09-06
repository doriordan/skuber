package skuber.rbac

/**
  * Created by jordan on 1/12/17.
  */
case class PolicyRule(apiGroups: List[String],
    attributeRestrictions: Option[String],
    nonResourceURLs: List[String],
    resourceNames: List[String],
    resources: List[String],
    verbs: List[String]
) {

}
