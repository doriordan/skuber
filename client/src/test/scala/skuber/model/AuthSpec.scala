package skuber.model

import org.specs2.mutable.Specification
import skuber.api.client.AuthInfo


/**
 * @author Clint Checketts
 */
class AuthSpec extends Specification {
  "This is a unit specification for the auth data model. ".txt
  
  
  // Auth
  "Auth toString works when empty" >> {
    AuthInfo().toString mustEqual "AuthInfo()"
  }

  "Auth toString masks cert, key, token, and password" >> {
    val auth = AuthInfo(
      clientCertificate = Option(Right("secretPem".getBytes)),
      clientKey = Option(Right("secretKey".getBytes)),
      token = Option("secretToken"),
      password = Option("goodPassword"))
    auth.toString mustEqual "AuthInfo(clientCertificate=<PEM masked> clientKey=<PEM masked> token=*********** password=************ )"
  }

  "Auth toString doesn't mask username, certPath, keyPath" >> {
    val auth = AuthInfo(
      clientCertificate = Option(Left("certPath")),
      clientKey = Option(Left("keyPath")),
      userName = Option("aUser")
    )
    auth.toString mustEqual "AuthInfo(clientCertificate=certPath clientKey=keyPath userName=aUser )"
  }

}