package skuber

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{Context, LoggingConfig, defaultAppConfig, defaultK8sConfig}

package object akkaclient {

  // aliases, references and delegates that enable using the API for many use cases without
  // having to import anything from the skuber.api package

  // Initialisation of the Skuber Kubernetes client based on Akka back-end

  import akka.actor.ActorSystem
  import com.typesafe.config.Config

  /**
    * Initialise Skuber using default Kubernetes and application configuration.
    */
  def k8sInit(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    k8sInit(defaultK8sConfig.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes configuration and default application configuration.
    */
  def k8sInit(config: skuber.api.Configuration)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    k8sInit(config.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  /**
    * Initialise Skuber using default Kubernetes configuration and the specified application configuration.
    */
  def k8sInit(appConfig: Config)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    k8sInit(defaultK8sConfig.currentContext, LoggingConfig(), None, appConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes and application configuration.
    */
  def k8sInit(config: skuber.api.Configuration, appConfig: Config)(implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    k8sInit(config.currentContext, LoggingConfig(), None, appConfig)
  }

  def k8sInit(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit] = None, appConfig: Config)
      (implicit actorSystem: ActorSystem): AkkaKubernetesClient = {
    AkkaKubernetesClientImpl(k8sContext, logConfig, closeHook, appConfig)
  }

  // Patch content type(s)
  final val `application/merge-patch+json`: MediaType.WithFixedCharset =
    MediaType.customWithFixedCharset("application", "merge-patch+json", HttpCharsets.`UTF-8`)

}
