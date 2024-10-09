package skuber

import scala.util.Try
import akka.actor.ActorSystem
import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.typesafe.config.Config
import skuber.akkaclient.impl.AkkaKubernetesClientImpl
import skuber.api.client.{Context, LoggingConfig, defaultAppConfig, defaultK8sConfig}

package object akkaclient {

  type AkkaSB = Source[ByteString, _]
  type AkkaSI = Source[String, _]
  type AkkaSO = Sink[String, _]

  object CustomMediaTypes {
    val `application/merge-patch+json`: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("merge-patch+json", HttpCharsets.`UTF-8`)
    val `application/strategic-merge-patch+json`: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("strategic-merge-patch+json", HttpCharsets.`UTF-8`)
  }

  type Pool[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]

  // Initialisation of the Skuber Kubernetes client based on Akka back-end

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

}
