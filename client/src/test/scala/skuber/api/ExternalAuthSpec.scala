package skuber.api

import java.time.Instant

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import skuber.api.client._

/**
 * @author C. G. Baker
 */
class ExternalAuthSpec extends Specification with Mockito {
  "This is a unit specification for the external auth classes. ".txt

  "GcpAuth passes command and all args" >> {
    val executioner = mock[CommandExecutioner]
    executioner.execute(any, any) returns
      """
        |{
        |  "credential": {
        |    "access_token": "the-token",
        |    "token_expiry": "2006-01-02T15:04:05Z"
        |  }
        |}
      """.stripMargin

    val auth = GcpAuth(accessToken = "MyAccessToken", expiry = Instant.now, cmdPath = "gcp", cmdArgs = "1 2")(executioner)
    auth.accessToken must_== "the-token"
    there was one(executioner).execute(
      command = Seq("gcp", "1", "2"),
      env = Seq.empty
    )
  }

  "ExecAuth passes env, command and all args" >> {
    val executioner = mock[CommandExecutioner]
    executioner.execute(any, any) returns
      s"""
        |{
        |  "apiVersion": "client.authentication.k8s.io/v1alpha1",
        |  "kind": "ExecCredential",
        |  "status": {
        |    "token": "the-token",
        |    "expirationTimestamp": "2006-01-02T15:04:05Z"
        |  }
        |}
      """.stripMargin
    val auth = ExecAuth("/usr/local/bin/some-command", Seq("1", "2"), Seq("3" -> "4", "5" -> "6"))(executioner)
    auth.accessToken must_== "the-token"
    there was one(executioner).execute(
      command = Seq("/usr/local/bin/some-command", "1", "2"),
      env = Seq("3" -> "4", "5" -> "6")
    )
  }

}