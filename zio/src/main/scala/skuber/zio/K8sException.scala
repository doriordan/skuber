package skuber.zio

import skuber.api.client.Status

final case class K8sException(status: Status)
  extends Exception(status.message.getOrElse(
    s"Kubernetes API error: HTTP ${status.code.getOrElse("unknown")}")):

  def code: Option[Int]       = status.code
  def isNotFound: Boolean     = code.contains(404)
  def isConflict: Boolean     = code.contains(409)
  def isUnauthorized: Boolean = code.contains(401)
