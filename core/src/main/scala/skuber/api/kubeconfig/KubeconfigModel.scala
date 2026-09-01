package skuber.api.kubeconfig

import java.time.Instant
import java.time.format.DateTimeFormatter

import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.{Success, Try}

/**
 * Explicit modelling of the kubeconfig.v1 schema (https://kubernetes.io/docs/reference/config-api/kubeconfig.v1/)
 * as parsed from the JSON produced by [[KubeconfigLoader]] (itself converted from YAML via SnakeYAML + Jackson).
 *
 * These are intentionally *raw*, one-to-one representations of the on-disk schema - conversion to skuber's
 * runtime `skuber.api.client.Cluster`/`Context`/`AuthInfo`/`skuber.api.Configuration` types (which must remain
 * binary compatible, so cannot simply grow new fields) happens separately in [[KubeconfigConverter]].
 *
 * Deliberately not modelled: `preferences`, impersonation (`as`/`as-uid`/`as-groups`/`as-user-extra`),
 * cluster-level `tls-server-name`/`proxy-url`/`disable-compression`. None of these appear in real-world
 * kubeconfig files skuber needs to support today, and the latter three would require new fields on the
 * runtime `Cluster`/`Context` case classes to have any effect - a binary-incompatible change deferred to a
 * future major version.
 */
private[kubeconfig] object KubeconfigInstant {
  private val formatters = List(DateTimeFormatter.ISO_INSTANT, DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  /**
   * Best-effort instant parsing that never fails - an unrecognized shape simply yields `None`. This mirrors
   * the pre-rewrite behavior of the legacy `auth-provider: gcp` config's `expiry` field, where an unparseable
   * date was historically treated as "no expiry" rather than a hard parse failure, and is reused for the
   * exec-credential response's `status.expirationTimestamp` for the same reason: a plugin returning a
   * malformed timestamp shouldn't make the whole credential fetch fail, just fall back to a conservative
   * refresh interval.
   */
  def parseLenient(value: JsValue): Option[Instant] = value match {
    case JsNumber(millis) => Some(Instant.ofEpochMilli(millis.toLong))
    case JsString(s) => formatters.iterator.map(fmt => Try(Instant.from(fmt.parse(s)))).collectFirst { case Success(i) => i }
    case _ => None
  }

  def readsLenientOptional(path: JsPath): Reads[Option[Instant]] =
    path.readNullable[JsValue].map(_.flatMap(parseLenient))
}

private[kubeconfig] final case class RawNamedExtension(name: String, extension: JsValue)

private[kubeconfig] object RawNamedExtension {
  implicit val reads: Reads[RawNamedExtension] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "extension").read[JsValue]
    )(RawNamedExtension.apply _)

  val readsList: Reads[List[RawNamedExtension]] =
    (JsPath \ "extensions").readNullable[List[RawNamedExtension]].map(_.getOrElse(Nil))
}

private[kubeconfig] final case class RawExecEnvVar(name: String, value: String)

private[kubeconfig] object RawExecEnvVar {
  implicit val reads: Reads[RawExecEnvVar] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "value").read[String]
    )(RawExecEnvVar.apply _)
}

private[kubeconfig] final case class RawExecConfig(
  command: String,
  args: List[String],
  env: List[RawExecEnvVar],
  apiVersion: String,
  installHint: Option[String],
  provideClusterInfo: Boolean,
  interactiveMode: String
)

private[kubeconfig] object RawExecConfig {
  implicit val reads: Reads[RawExecConfig] = (
    (JsPath \ "command").read[String] and
      (JsPath \ "args").readNullable[List[String]].map(_.getOrElse(Nil)) and
      (JsPath \ "env").readNullable[List[RawExecEnvVar]].map(_.getOrElse(Nil)) and
      (JsPath \ "apiVersion").read[String] and
      (JsPath \ "installHint").readNullable[String] and
      (JsPath \ "provideClusterInfo").readNullable[Boolean].map(_.getOrElse(false)) and
      (JsPath \ "interactiveMode").readNullable[String].map(_.getOrElse("IfAvailable"))
    )(RawExecConfig.apply _)
}

private[kubeconfig] final case class RawAuthProvider(
  name: String,
  idToken: Option[String],
  accessToken: Option[String],
  expiry: Option[Instant],
  cmdPath: Option[String],
  cmdArgs: Option[String]
)

private[kubeconfig] object RawAuthProvider {
  implicit val reads: Reads[RawAuthProvider] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "config" \ "id-token").readNullable[String] and
      (JsPath \ "config" \ "access-token").readNullable[String] and
      KubeconfigInstant.readsLenientOptional(JsPath \ "config" \ "expiry") and
      (JsPath \ "config" \ "cmd-path").readNullable[String] and
      (JsPath \ "config" \ "cmd-args").readNullable[String]
    )(RawAuthProvider.apply _)
}

private[kubeconfig] final case class RawAuthInfo(
  clientCertificate: Option[String],
  clientCertificateData: Option[String],
  clientKey: Option[String],
  clientKeyData: Option[String],
  token: Option[String],
  tokenFile: Option[String],
  username: Option[String],
  password: Option[String],
  authProvider: Option[RawAuthProvider],
  exec: Option[RawExecConfig],
  extensions: List[RawNamedExtension]
)

private[kubeconfig] object RawAuthInfo {
  implicit val reads: Reads[RawAuthInfo] = (
    (JsPath \ "client-certificate").readNullable[String] and
      (JsPath \ "client-certificate-data").readNullable[String] and
      (JsPath \ "client-key").readNullable[String] and
      (JsPath \ "client-key-data").readNullable[String] and
      (JsPath \ "token").readNullable[String] and
      (JsPath \ "tokenFile").readNullable[String] and
      (JsPath \ "username").readNullable[String] and
      (JsPath \ "password").readNullable[String] and
      (JsPath \ "auth-provider").readNullable[RawAuthProvider] and
      (JsPath \ "exec").readNullable[RawExecConfig] and
      RawNamedExtension.readsList
    )(RawAuthInfo.apply _)
}

private[kubeconfig] final case class RawNamedUser(name: String, user: RawAuthInfo)

private[kubeconfig] object RawNamedUser {
  implicit val reads: Reads[RawNamedUser] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "user").read[RawAuthInfo]
    )(RawNamedUser.apply _)
}

private[kubeconfig] final case class RawCluster(
  apiVersion: Option[String],
  server: Option[String],
  insecureSkipTLSVerify: Option[Boolean],
  certificateAuthority: Option[String],
  certificateAuthorityData: Option[String],
  extensions: List[RawNamedExtension]
)

private[kubeconfig] object RawCluster {
  implicit val reads: Reads[RawCluster] = (
    (JsPath \ "api-version").readNullable[String] and
      (JsPath \ "server").readNullable[String] and
      (JsPath \ "insecure-skip-tls-verify").readNullable[Boolean] and
      (JsPath \ "certificate-authority").readNullable[String] and
      (JsPath \ "certificate-authority-data").readNullable[String] and
      RawNamedExtension.readsList
    )(RawCluster.apply _)
}

private[kubeconfig] final case class RawNamedCluster(name: String, cluster: RawCluster)

private[kubeconfig] object RawNamedCluster {
  implicit val reads: Reads[RawNamedCluster] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "cluster").read[RawCluster]
    )(RawNamedCluster.apply _)
}

private[kubeconfig] final case class RawContext(
  cluster: Option[String],
  user: Option[String],
  namespace: Option[String],
  extensions: List[RawNamedExtension]
)

private[kubeconfig] object RawContext {
  implicit val reads: Reads[RawContext] = (
    (JsPath \ "cluster").readNullable[String] and
      (JsPath \ "user").readNullable[String] and
      (JsPath \ "namespace").readNullable[String] and
      RawNamedExtension.readsList
    )(RawContext.apply _)
}

private[kubeconfig] final case class RawNamedContext(name: String, context: RawContext)

private[kubeconfig] object RawNamedContext {
  implicit val reads: Reads[RawNamedContext] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "context").read[RawContext]
    )(RawNamedContext.apply _)
}

private[kubeconfig] final case class RawConfig(
  clusters: List[RawNamedCluster],
  users: List[RawNamedUser],
  contexts: List[RawNamedContext],
  currentContext: Option[String],
  extensions: List[RawNamedExtension]
)

private[kubeconfig] object RawConfig {
  implicit val reads: Reads[RawConfig] = (
    (JsPath \ "clusters").readNullable[List[RawNamedCluster]].map(_.getOrElse(Nil)) and
      (JsPath \ "users").readNullable[List[RawNamedUser]].map(_.getOrElse(Nil)) and
      (JsPath \ "contexts").readNullable[List[RawNamedContext]].map(_.getOrElse(Nil)) and
      (JsPath \ "current-context").readNullable[String] and
      RawNamedExtension.readsList
    )(RawConfig.apply _)
}
