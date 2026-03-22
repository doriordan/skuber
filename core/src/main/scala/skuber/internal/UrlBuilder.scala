package skuber.internal

import skuber.model.{ResourceDefinition, ResourceSpecification, TypeMeta}

object UrlBuilder {

  def resourceUrl(
    clusterServer: String,
    namespace: String,
    rd: ResourceDefinition[_ <: TypeMeta],
    nameComponent: Option[String] = None,
    namespaceOverride: Option[String] = None,
    clusterScopeOverride: Option[Boolean] = None
  ): String = {
    val nsPathComponent = clusterScopeOverride match {
      case None if rd.spec.scope == ResourceSpecification.Scope.Cluster => None
      case Some(true) => None
      case _ =>
        val ns = namespaceOverride.getOrElse(namespace)
        Some(s"namespaces/$ns")
    }

    val parts: List[Any] = List(
      clusterServer,
      rd.spec.apiPathPrefix,
      rd.spec.group,
      rd.spec.defaultVersion,
      nsPathComponent,
      rd.spec.names.plural,
      nameComponent
    )

    val urlParts = parts.collect {
      case p: String if p.nonEmpty        => p
      case Some(p: String) if p.nonEmpty  => p
    }

    urlParts.mkString("/")
  }

  def statusUrl(
    clusterServer: String,
    namespace: String,
    rd: ResourceDefinition[_ <: TypeMeta],
    name: String
  ): String =
    s"${resourceUrl(clusterServer, namespace, rd, Some(name))}/status"

  def scaleUrl(
    clusterServer: String,
    namespace: String,
    rd: ResourceDefinition[_ <: TypeMeta],
    name: String
  ): String =
    s"${resourceUrl(clusterServer, namespace, rd, Some(name))}/scale"

  def podLogUrl(
    clusterServer: String,
    namespace: String,
    podName: String
  ): String =
    s"$clusterServer/api/v1/namespaces/$namespace/pods/$podName/log"

  def execUrl(
    clusterServer: String,
    namespace: String,
    podName: String
  ): String =
    s"$clusterServer/api/v1/namespaces/$namespace/pods/$podName/exec"
}
