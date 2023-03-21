package skuber.api

import java.time.Instant
import java.util.UUID
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpCharsets, HttpRequest, HttpResponse, MediaType}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.Flow
import com.typesafe.config.{Config, ConfigFactory}
import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import skuber.ObjectResource
import skuber.api.client.impl.KubernetesClientImpl
import skuber.api.client.token.RefreshableToken
import scala.sys.SystemProperties
import scala.util.Try

/**
  * @author David O'Riordan
  */
package object client {

  type Pool[T] = Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]

  final val sysProps = new SystemProperties

  // Certificates and keys can be specified in configuration either as paths to files or embedded PEM data
  type PathOrData = Either[String, Array[Byte]]

  // K8S client API classes
  final val defaultApiServerURL = "http://localhost:8080"

  // Patch content type(s)
  final val `application/merge-patch+json`: MediaType.WithFixedCharset =
    MediaType.customWithFixedCharset("application", "merge-patch+json", HttpCharsets.`UTF-8`)

  sealed trait AuthInfo

  sealed trait AccessTokenAuth extends AuthInfo {
    def accessToken: String
  }

  object NoAuth extends AuthInfo {
    override def toString: String = "NoAuth"
  }

  final case class BasicAuth(userName: String, password: String) extends AuthInfo {
    override def toString: String = s"${getClass.getSimpleName}(userName=$userName,password=<redacted>)"
  }

  final case class TokenAuth(token: String) extends AccessTokenAuth {

    override def accessToken: String = token

    override def toString: String = s"${getClass.getSimpleName}(token=<redacted>)"
  }

  final case class CertAuth(clientCertificate: PathOrData, clientKey: PathOrData, user: Option[String]) extends AuthInfo {
    override def toString: String = StringBuilder.newBuilder
        .append(getClass.getSimpleName)
        .append("(")
        .append {
          clientCertificate match {
            case Left(certPath: String) => "clientCertificate=" + certPath + " "
            case Right(_) => "clientCertificate=<PEM masked> "
          }
        }
        .append {
          clientKey match {
            case Left(certPath: String) => "clientKey=" + certPath + " "
            case Right(_) => "clientKey=<PEM masked> "
          }
        }
        .append("userName=")
        .append(user.getOrElse(""))
        .append(" )")
        .mkString
  }

  trait AuthProviderAuth extends AccessTokenAuth {
    def name: String
  }

  trait AuthProviderRefreshableAuth extends AuthProviderAuth {
    def refreshToken: RefreshableToken
    def generateToken: String
    def name: String
    def isTokenExpired(refreshableToken: RefreshableToken): Boolean
  }

  // 'jwt' supports an oidc id token per https://kubernetes.io/docs/admin/authentication/#option-1---oidc-authenticator
  // - but does not yet support token refresh
  final case class OidcAuth(idToken: String) extends AuthProviderAuth {
    override val name = "oidc"

    override def accessToken: String = idToken

    override def toString = """OidcAuth(idToken=<redacted>)"""
  }

  final case class GcpAuth private(private val config: GcpConfiguration) extends AuthProviderRefreshableAuth {
    override val name = "gcp"

    @volatile private var refresh: Option[RefreshableToken] = config.cachedAccessToken.map(token => GcpRefresh(token.accessToken, token.expiry).toRefreshableToken)

    override def refreshToken: RefreshableToken = {
      val output = generateToken
      val parsed = Json.parse(output).as[GcpRefresh].toRefreshableToken
      refresh = Some(parsed)
      parsed
    }

    def accessToken: String = this.synchronized {
      refresh match {
        case Some(token) if isTokenExpired(token) =>
          refreshToken.accessToken
        case None =>
          refreshToken.accessToken
        case Some(token) =>
          token.accessToken
      }
    }

    override def toString =
      """GcpAuth(accessToken=<redacted>)""".stripMargin

    override def isTokenExpired(refreshableToken: RefreshableToken): Boolean = {
      DateTime.now.isAfter(refreshableToken.expiry.minusSeconds(20))
    }
    override def generateToken: String = config.cmd.execute()
  }

  final private[client] case class GcpRefresh(accessToken: String, expiry: Instant) {

    def toRefreshableToken: RefreshableToken = {
      val expirationDate = new DateTime(this.expiry.toEpochMilli)
      RefreshableToken(accessToken = this.accessToken, expiry = expirationDate)
    }
  }

  private[client] object GcpRefresh {
    // todo - the path to read this from is part of the configuration, use that instead of
    // hard coding.
    implicit val gcpRefreshReads: Reads[GcpRefresh] = ((JsPath \ "credential" \ "access_token").read[String] and
            (JsPath \ "credential" \ "token_expiry").read[Instant]) (GcpRefresh.apply _)
  }

  final case class GcpConfiguration(cachedAccessToken: Option[GcpCachedAccessToken], cmd: GcpCommand)

  final case class GcpCachedAccessToken(accessToken: String, expiry: Instant)

  final case class GcpCommand(cmd: String, args: String) {

    import scala.sys.process._

    def execute(): String = s"$cmd $args".!!
  }

  object GcpAuth {
    def apply(accessToken: Option[String], expiry: Option[Instant], cmdPath: String, cmdArgs: String): GcpAuth = {
      val cachedAccessToken = for {
        token <- accessToken
        exp <- expiry
      } yield GcpCachedAccessToken(token, exp)
      new GcpAuth(GcpConfiguration(cachedAccessToken = cachedAccessToken,
          GcpCommand(cmdPath, cmdArgs)))
    }
  }

  type WatchEventWrapper[T <: ObjectResource] = Either[Status, WatchEvent[T]]

  // for use with the Watch command
  case class WatchEvent[T <: ObjectResource](_type: EventType.Value, _object: T)

  object EventType extends Enumeration {
    type EventType = Value
    val ADDED, MODIFIED, DELETED, ERROR = Value
  }

  trait LoggingContext {
    def output: String
  }

  object LoggingContext {
    implicit def lc : LoggingContext = RequestLoggingContext()
  }

  case class RequestLoggingContext(requestId: String) extends LoggingContext {
    def output = s"{ reqId=$requestId} }"
  }

  object RequestLoggingContext {
    def apply(): RequestLoggingContext = new RequestLoggingContext(UUID.randomUUID.toString)
  }

  class K8SException(val status: Status) extends RuntimeException(status.toString) // we throw this when we receive a non-OK response

  type RequestContext = KubernetesClient // for backwards compatibility (with pre 2.1 clients)

  def init()(implicit actorSystem: ActorSystem): KubernetesClient = {
    init(defaultK8sConfig, defaultAppConfig)
  }

  def init(config: Configuration)(implicit actorSystem: ActorSystem): KubernetesClient = {
    init(config.currentContext, LoggingConfig(), None, defaultAppConfig, None)
  }

  def init(appConfig: Config)(implicit actorSystem: ActorSystem): KubernetesClient = {
    init(defaultK8sConfig.currentContext, LoggingConfig(), None, appConfig, None)
  }

  def init(config: Configuration, appConfig: Config)(implicit actorSystem: ActorSystem): KubernetesClient = {
    init(config.currentContext, LoggingConfig(), None, appConfig, None)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit] = None)
      (implicit actorSystem: ActorSystem): KubernetesClient = {
    init(k8sContext, logConfig, closeHook, defaultAppConfig, None)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit], appConfig: Config)
      (implicit actorSystem: ActorSystem): KubernetesClient = {
    KubernetesClientImpl(k8sContext, logConfig, closeHook, appConfig, None)
  }

  def init(k8sContext: Context, logConfig: LoggingConfig, closeHook: Option[() => Unit], appConfig: Config, connectionPoolSettings: Option[ConnectionPoolSettings])
      (implicit actorSystem: ActorSystem): KubernetesClient = {
    KubernetesClientImpl(k8sContext, logConfig, closeHook, appConfig, connectionPoolSettings)
  }

  def defaultK8sConfig: Configuration = Configuration.defaultK8sConfig

  private def defaultAppConfig: Config = ConfigFactory.load()
}
