package skuber.model

import java.time.Instant

import org.specs2.mutable.Specification
import skuber.api.client._


/**
 * @author Clint Checketts
 */
class AuthSpec extends Specification {
  "This is a unit specification for the auth data model. ".txt
  
  
  // Auth
  "Auth toString works when empty" >> {
    NoAuth.toString mustEqual "NoAuth"
  }

  "CertAuth toString masks cert, key but not user" >> {
    val auth = CertAuth(clientCertificate = Right("secretPem".getBytes),
      clientKey = Right("secretKey".getBytes),
      user = Some("someUser"))
    auth.toString mustEqual "CertAuth(clientCertificate=<PEM masked> clientKey=<PEM masked> userName=someUser )"
  }

  "CertAuth toString doesn't mask username, certPath, keyPath" >> {
    val auth = CertAuth(
      clientCertificate = Left("certPath"),
      clientKey = Left("keyPath"),
      user = Option("aUser")
    )
    auth.toString mustEqual "CertAuth(clientCertificate=certPath clientKey=keyPath userName=aUser )"
  }

  "TokenAuth toString masks token" >> {
    TokenAuth("myToken").toString mustEqual "TokenAuth(token=<redacted>)"
  }

  "GcpAuth toString masks accessToken" >> {
    GcpAuth(accessToken = Some("MyAccessToken"), expiry = Some(Instant.now), cmdPath = "gcp", cmdArgs = "").toString mustEqual
      "GcpAuth(accessToken=<redacted>)"
  }

  "OidcAuth toString masks idToken" >> {
    OidcAuth(idToken = "MyToken").toString mustEqual "OidcAuth(idToken=<redacted>)"
  }

}