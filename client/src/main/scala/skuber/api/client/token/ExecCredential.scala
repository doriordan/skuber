package skuber.api.client.token

import play.api.libs.json.{Json, OFormat}

final case class ExecCredential(status: ExecCredentialStatus)

object ExecCredential {
  implicit val execCredentialFmt: OFormat[ExecCredential] = Json.format[ExecCredential]
}
