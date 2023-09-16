package skuber.api

import java.time.Instant
import java.util.UUID

import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.sys.SystemProperties
import skuber.model.ObjectResource

/**
  * @author David O'Riordan
  */
package object client {

  case class WatchedEvent(eventType: WatchedEventType.Value, eventObject: ObjectResource)

  object WatchedEventType extends Enumeration {
    type WatchedEventType = Value
    val ADDED, MODIFIED, DELETED, ERROR = Value
  }

  // Delete options are (optionally) passed with a Delete request
  object DeletePropagation extends Enumeration {
    type DeletePropagation = Value
    val Orphan, Background, Foreground = Value
  }

  case class Preconditions(uid: String = "")

  case class DeleteOptions(
    apiVersion: String = "v1",
    kind: String = "DeleteOptions",
    gracePeriodSeconds: Option[Int] = None,
    preconditions: Option[Preconditions] = None,
    propagationPolicy: Option[DeletePropagation.Value] = None)

  // List options can be passed to a list or watch request.
  case class ListOptions(
    labelSelector: Option[skuber.model.LabelSelector] = None,
    fieldSelector: Option[String] = None,
    includeUninitialized: Option[Boolean] = None,
    resourceVersion: Option[String] = None,
    timeoutSeconds: Option[Long] = None,
    limit: Option[Long] = None,
    continue: Option[String] = None,
    watch: Option[Boolean] = None // NOTE: not for application use - it will be overridden by watch requests
  ) {
    lazy val asOptionalsMap: Map[String, Option[String]] = Map(
      "labelSelector" -> labelSelector.map(_.toString),
      "fieldSelector" -> fieldSelector,
      "includeUninitialized" -> includeUninitialized.map(_.toString),
      "resourceVersion" -> resourceVersion,
      "timeoutSeconds" -> timeoutSeconds.map(_.toString),
      "limit" -> limit.map(_.toString),
      "continue" -> continue,
      "watch" -> watch.map(_.toString))

    lazy val asMap: Map[String, String] = asOptionalsMap.collect {
      case (key, Some(value)) => key -> value
    }
  }

  final val sysProps = new SystemProperties

  // Certificates and keys can be specified in configuration either as paths to files or embedded PEM data
  type PathOrData = Either[String, Array[Byte]]

  // K8S client API classes
  final val defaultApiServerURL = "http://localhost:8080"

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
    override def toString: String = new StringBuilder()
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

  sealed trait AuthProviderAuth extends AccessTokenAuth {
    def name: String
  }

  // 'jwt' supports an oidc id token per https://kubernetes.io/docs/admin/authentication/#option-1---oidc-authenticator
  // - but does not yet support token refresh
  final case class OidcAuth(idToken: String) extends AuthProviderAuth {
    override val name = "oidc"

    override def accessToken: String = idToken

    override def toString = """OidcAuth(idToken=<redacted>)"""
  }

  final case class GcpAuth private(private val config: GcpConfiguration) extends AuthProviderAuth {
    override val name = "gcp"

    @volatile private var refresh: Option[GcpRefresh] = config.cachedAccessToken.map(token => GcpRefresh(token.accessToken, token.expiry))

    private def refreshGcpToken(): GcpRefresh = {
      val output = config.cmd.execute()
      val parsed = Json.parse(output).as[GcpRefresh]
      refresh = Some(parsed)
      parsed
    }

    def accessToken: String = this.synchronized {
      refresh match {
        case Some(expired) if expired.expired =>
          refreshGcpToken().accessToken
        case None =>
          refreshGcpToken().accessToken
        case Some(token) =>
          token.accessToken
      }
    }

    override def toString: String =
      """GcpAuth(accessToken=<redacted>)""".stripMargin
  }

  final private[client] case class GcpRefresh(accessToken: String, expiry: Instant) {
    def expired: Boolean = Instant.now.isAfter(expiry.minusSeconds(20))
  }

  private[client] object GcpRefresh {
    // todo - the path to read this from is part of the configuration, use that instead of
    // hard coding.
    implicit val gcpRefreshReads: Reads[GcpRefresh] = (
        (JsPath \ "credential" \ "access_token").read[String] and
            (JsPath \ "credential" \ "token_expiry").read[Instant]
        ) (GcpRefresh.apply _)
  }

  final case class GcpConfiguration(cachedAccessToken: Option[GcpCachedAccessToken], cmd: GcpCommand)

  final case class GcpCachedAccessToken(accessToken: String, expiry: Instant) {
    def expired: Boolean = Instant.now.isAfter(expiry.minusSeconds(20))
  }

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
      new GcpAuth(
        GcpConfiguration(
          cachedAccessToken = cachedAccessToken,
          GcpCommand(cmdPath, cmdArgs)
        )
      )
    }
  }

  // for use with the Watch command
  case class WatchEvent[T <: ObjectResource](_type: EventType.Value, _object: T)

  object EventType extends Enumeration {
    type EventType = Value
    val ADDED, MODIFIED, DELETED, ERROR = Value
  }

  object WatchStream {

    sealed trait StreamElement[O <: ObjectResource] {}
    case class End[O <: ObjectResource]() extends StreamElement[O]
    case class Start[O <: ObjectResource](resourceVersion: Option[String]) extends StreamElement[O]
    case class Result[O <: ObjectResource](resourceVersion: String, value: WatchEvent[O]) extends StreamElement[O]

    sealed trait StreamState {}
    case object Waiting extends StreamState
    case object Processing extends StreamState
    case object Finished extends StreamState

    case class StreamContext(currentResourceVersion: Option[String], state: StreamState)
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

  def defaultK8sConfig: Configuration = Configuration.defaultK8sConfig

  def defaultAppConfig: Config = ConfigFactory.load()

  val K8SCluster: Cluster.type = skuber.api.client.Cluster
  val K8SContext: Context.type = skuber.api.client.Context
  val K8SConfiguration: Configuration.type = skuber.api.Configuration
  type K8SWatchEvent[I <: skuber.model.ObjectResource] = skuber.api.client.WatchEvent[I]
}
