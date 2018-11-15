package skuber.api.client

import skuber.ListMeta

/**
  * @author David O'Riordan
  * Represents the status information typically returned in error responses from the Kubernetes API
  */
case class Status(
  apiVersion: String = "v1",
  kind: String = "Status",
  metadata: ListMeta = ListMeta(),
  status: Option[String] = None,
  message: Option[String]= None,
  reason: Option[String] = None,
  details: Option[Any] = None,
  code: Option[Int] = None  // HTTP status code
)
