package skuber

import scala.util.Try
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.NotUsed
import org.apache.pekko.http.scaladsl.model.{HttpCharsets, MediaType}
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.stream.scaladsl.{Flow, Source, Sink}
import org.apache.pekko.util.ByteString
import com.typesafe.config.Config
import skuber.pekkoclient.impl.PekkoKubernetesClientImpl
import skuber.api.client.{Context, LoggingConfig, defaultAppConfig, defaultK8sConfig}

package object pekkoclient {

  type PekkoSB = Source[ByteString, _]
  type PekkoSI = Source[String, _]
  type PekkoSO = Sink[String, _]

  object CustomMediaTypes {
    val `application/merge-patch+json`: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("merge-patch+json", HttpCharsets.`UTF-8`)
    val `application/strategic-merge-patch+json`: MediaType.WithFixedCharset = MediaType.applicationWithFixedCharset("strategic-merge-patch+json", HttpCharsets.`UTF-8`)
  }

  type Pool[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]

  // Initialisation of the Skuber Kubernetes client based on Akka back-end

  /**
    * Initialise Skuber using default Kubernetes and application configuration.
    */
  def k8sInit(implicit actorSystem: ActorSystem): PekkoKubernetesClient = {
    k8sInit(defaultK8sConfig.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes configuration and default application configuration.
    */
  def k8sInit(config: skuber.api.Configuration)(implicit actorSystem: ActorSystem): PekkoKubernetesClient = {
    k8sInit(config.currentContext, LoggingConfig(), None, defaultAppConfig)
  }

  /**
    * Initialise Skuber using default Kubernetes configuration and the specified application configuration.
    */
  def k8sInit(appConfig: Config)(implicit actorSystem: ActorSystem): PekkoKubernetesClient = {
    k8sInit(defaultK8sConfig.currentContext, LoggingConfig(), None, appConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes and application configuration.
    */
  def k8sInit(config: skuber.api.Configuration, appConfig: Config)(implicit actorSystem: ActorSystem): PekkoKubernetesClient = {
    k8sInit(config.currentContext, LoggingConfig(), None, appConfig)
  }

  def k8sInit(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit] = None, appConfig: Config)
      (implicit actorSystem: ActorSystem): PekkoKubernetesClient = {
    PekkoKubernetesClientImpl(k8sContext, logConfig, closeHook, appConfig)
  }
}
