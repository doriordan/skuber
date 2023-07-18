
import skuber.model.{Pod, ReplicationController, Resource, ResourceDefinition, Secret, Service, ServiceAccount}

import scala.language.implicitConversions
import java.net.URL

/*
 * Represents some convenient commonly used aliases and initialisation methods
 * @author David O'Riordan
 */
package object skuber {

  // aliases, references and delegates that enable using the API for many use cases without 
  // having to import anything from the skuber.api package

  import skuber.api.client.KubernetesClient

  val K8SCluster = skuber.api.client.Cluster
  val K8SContext = skuber.api.client.Context
  type K8SRequestContext = KubernetesClient
  type K8SException = skuber.api.client.K8SException
  val K8SConfiguration = skuber.api.Configuration
  type K8SWatchEvent[I <: skuber.model.ObjectResource] = skuber.api.client.WatchEvent[I]

  // Initialisation of the Skuber Kubernetes client

  import akka.actor.ActorSystem
  import akka.stream.Materializer
  import com.typesafe.config.Config

  /**
    * Initialise Skuber using default Kubernetes and application configuration.
    */
  def k8sInit(implicit actorSystem: ActorSystem): KubernetesClient = {
    skuber.api.client.init
  }

  /**
    * Initialise Skuber using the specified Kubernetes configuration and default application configuration.
    */
  def k8sInit(config: skuber.api.Configuration)(implicit actorSystem: ActorSystem): KubernetesClient = {
    skuber.api.client.init(config)
  }

  /**
    * Initialise Skuber using default Kubernetes configuration and the specified application configuration.
    */
  def k8sInit(appConfig: Config)(implicit actorSystem: ActorSystem): KubernetesClient = {
    skuber.api.client.init(appConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes and application configuration.
    */
  def k8sInit(config: skuber.api.Configuration, appConfig: Config)(implicit actorSystem: ActorSystem)
      : KubernetesClient =
  {
    skuber.api.client.init(config, appConfig)
  }
}
