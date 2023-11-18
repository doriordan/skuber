package skuber.api.client.token

import java.nio.charset.StandardCharsets
import java.time.{ZoneId, ZonedDateTime}
import java.util.TimeZone
import org.apache.commons.io.IOUtils
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import skuber.api.client.AuthProviderRefreshableAuth
import scala.collection.JavaConverters._

// https://kubernetes.io/docs/reference/config-api/kubeconfig.v1/#ExecConfig
final case class ExecAuthRefreshable(config: ExecAuthConfig) extends AuthProviderRefreshableAuth {
  override val name = "exec"

  @volatile private var cachedToken: Option[RefreshableToken] = None

  override def refreshToken: RefreshableToken = {
    val output = generateToken
    val parsed = Json.parse(output).as[ExecCredential]
    val refreshableToken = toRefreshableToken(parsed)
    cachedToken = Some(refreshableToken)
    refreshableToken
  }

  def accessToken: String = this.synchronized {
    cachedToken match {
      case Some(token) if isTokenExpired(token) =>
        refreshToken.accessToken
      case None =>
        refreshToken.accessToken
      case Some(token) =>
        token.accessToken
    }
  }

  override def toString =
    """ExecAuthRefreshable(accessToken=<redacted>)""".stripMargin

  override def isTokenExpired(refreshableToken: RefreshableToken): Boolean = {
    DateTime.now(DateTimeZone.UTC).isAfter(refreshableToken.expiry)
  }

  override def generateToken: String = config.execute()

  private def toRefreshableToken(execCredential: ExecCredential): RefreshableToken = {
    val utc = ZoneId.of("UTC")
    val now = ZonedDateTime.now(utc)
    val expiration = execCredential.status.expirationTimestamp.getOrElse(now.plusYears(1))
    val expirationDateTime =  new DateTime(expiration.toInstant.toEpochMilli, DateTimeZone.forTimeZone(TimeZone.getTimeZone(utc)))

    RefreshableToken(execCredential.status.token, expirationDateTime)
  }
}

final case class ExecAuthConfig(cmd: String,
                                args: List[String],
                                envVariables: Map[String, String]) {
  def execute(): String = {
    val process = new java.lang.ProcessBuilder((Seq(cmd) ++ args).toList.asJava)
    envVariables.map { case (name, value) => process.environment().put(name, value)}
    val output = IOUtils.toString(process.start.getInputStream, StandardCharsets.UTF_8)
    output
  }
}
