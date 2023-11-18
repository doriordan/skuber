package skuber.api.client.token

import play.api.libs.json.{Json, OFormat}
import skuber.Timestamp

final case class ExecCredentialStatus(expirationTimestamp: Option[Timestamp], token: String)

object ExecCredentialStatus {
  implicit val execCredentialStatusFmt: OFormat[ExecCredentialStatus] = Json.format[ExecCredentialStatus]
}