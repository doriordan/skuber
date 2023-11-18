package skuber.api.client.token

import java.time.{ZoneId, ZonedDateTime}
import java.util.TimeZone
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import skuber.api.client.AuthProviderRefreshableAuth

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
    DateTime.now.isAfter(refreshableToken.expiry.minusSeconds(20))
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

  import scala.sys.process._

  def execute(): String = {
    Process(
      command = Seq(cmd) ++ args,
      extraEnv = envVariables.toSeq: _*,
      cwd = None
    ).!!
  }
}
