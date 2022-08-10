package skuber.rbac

/**
  * Created by jordan on 1/13/17.
  */
case class Subject(apiVersion: Option[String],
    kind: String,
    name: String,
    namespace: Option[String]
) {

}
