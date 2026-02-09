package skuber.operator.reconciler

import skuber.model.ObjectResource

/**
 * Unique identifier for a Kubernetes resource within a cluster.
 * Combines namespace and name.
 */
case class NamespacedName(namespace: String, name: String):
  override def toString: String =
    if namespace.isEmpty then name else s"$namespace/$name"

object NamespacedName:
  /**
   * Extract NamespacedName from an ObjectResource.
   */
  def apply[R <: ObjectResource](resource: R): NamespacedName =
    NamespacedName(
      resource.metadata.namespace,
      resource.metadata.name
    )

  /**
   * Parse from string format "namespace/name" or just "name" for cluster-scoped.
   */
  def fromString(s: String): NamespacedName =
    s.split("/", 2) match
      case Array(ns, name) => NamespacedName(ns, name)
      case Array(name)     => NamespacedName("", name)
      case _               => NamespacedName("", s)
