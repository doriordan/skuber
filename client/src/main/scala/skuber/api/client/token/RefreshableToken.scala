package skuber.api.client.token

import org.joda.time.DateTime

final case class RefreshableToken(accessToken: String, expiry: DateTime)
