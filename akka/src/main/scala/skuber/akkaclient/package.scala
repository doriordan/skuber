package skuber

import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{Context, LoggingConfig, defaultAppConfig, defaultK8sConfig, init}

package object akkaclient {

  // aliases, references and delegates that enable using the API for many use cases without
  // having to import anything from the skuber.api package

  import skuber.api.client.KubernetesClient

  // Initialisation of the Skuber Kubernetes client

  import akka.actor.ActorSystem
  import akka.stream.Materializer
  import com.typesafe.config.Config

  /**
    * Initialise Skuber using default Kubernetes and application configuration.
    */
  def k8sInit(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    init(defaultK8sConfig, defaultAppConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes configuration and default application configuration.
    */
  def k8sInit(config: skuber.api.Configuration)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    init(config.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  /**
    * Initialise Skuber using default Kubernetes configuration and the specified application configuration.
    */
  def k8sInit(appConfig: Config)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    init(defaultK8sConfig.currentContext, LoggingConfig(), None, appConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes and application configuration.
    */
  def k8sInit(config: skuber.api.Configuration, appConfig: Config)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    init(config.currentContext, LoggingConfig(), None, appConfig)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit] = None, appConfig: Config)
      (implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    AkkaKubernetesClientImpl(k8sContext, logConfig, closeHook, appConfig)
  }
}
