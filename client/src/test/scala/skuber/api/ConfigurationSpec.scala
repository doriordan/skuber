package skuber.api

import skuber._
import org.specs2.mutable.Specification
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.util.Try
import skuber.api.client._

/**
 * @author David O'Riordan
 */
class ConfigurationSpec extends Specification {
    val kubeConfigStr =  """
apiVersion: v1
clusters:
- cluster:
    api-version: v1
    server: http://cow.org:8080
  name: cow-cluster
- cluster:
    certificate-authority: path/to/my/cafile
    server: https://horse.org:4443
  name: horse-cluster
- cluster:
    insecure-skip-tls-verify: true
    server: https://pig.org:443
  name: pig-cluster
contexts:
- context:
    cluster: horse-cluster
    namespace: chisel-ns
    user: green-user
  name: federal-context
- context:
    cluster: pig-cluster
    namespace: saw-ns
    user: blue-user
  name: queen-anne-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: blue-user
  user:
    token: blue-token
- name: green-user
  user:
    client-certificate: path/to/my/client/cert
    client-key: path/to/my/client/key
- name: jwt-user
  user:
    auth-provider:
      config:
        client-id: tectonic
        client-secret: secret
        extra-scopes: groups
        id-token: jwt-token
        idp-certificate-authority-data: data
        idp-issuer-url: https://xyz/identity
        refresh-token: refresh
      name: oidc
- name: gke-user
  user:
    auth-provider:
      config:
        access-token: myAccessToken
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: 2018-03-04T14:08:18Z
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
- name: string-date-gke-user
  user:
    auth-provider:
      config:
        access-token: myAccessToken
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: "2018-03-04T14:08:18Z"
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
- name: other-date-gke-user
  user:
    auth-provider:
      config:
        cmd-args: config config-helper --format=json
        cmd-path: /home/user/google-cloud-sdk/bin/gcloud
        expiry: "2018-03-04 14:08:18"
        expiry-key: '{.credential.token_expiry}'
        token-key: '{.credential.access_token}'
      name: gcp
"""

  implicit val system: ActorSystem =ActorSystem("test")
  implicit val loggingContext: LoggingContext = new LoggingContext { override def output:String="test" }

  "An example kubeconfig file can be parsed correctly" >> {
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is)
    val parsedFromStringConfig = k8sConfig.get
    
    // construct equivalent config directly for comparison
 
    val cowCluster=K8SCluster("v1", "http://cow.org:8080",false, clusterName = Some("cow-cluster"))
    val horseCluster=K8SCluster("v1","https://horse.org:4443", false, certificateAuthority=Some(Left("path/to/my/cafile")), clusterName = Some("horse-cluster"))
    val pigCluster=K8SCluster("v1", "https://pig.org:443", true, clusterName = Some("pig-cluster"))
    val clusters=Map("cow-cluster" -> cowCluster,"horse-cluster"->horseCluster,"pig-cluster"->pigCluster)
    
    val blueUser = TokenAuth("blue-token")
    val greenUser = CertAuth(clientCertificate = Left("path/to/my/client/cert"), clientKey = Left("path/to/my/client/key"), user = None)
    val jwtUser= OidcAuth(idToken = "jwt-token")
    val gcpUser = GcpAuth(accessToken = Some("myAccessToken"), expiry = Some(Instant.parse("2018-03-04T14:08:18Z")),
      cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
    val noAccessTokenGcpUser = GcpAuth(accessToken = None, expiry = None,
      cmdPath = "/home/user/google-cloud-sdk/bin/gcloud", cmdArgs = "config config-helper --format=json")
    val users=Map("blue-user"->blueUser,"green-user"->greenUser,"jwt-user"->jwtUser, "gke-user"->gcpUser,
      "string-date-gke-user"->gcpUser, "other-date-gke-user" -> noAccessTokenGcpUser)

    val federalContext=K8SContext(horseCluster,greenUser,Namespace.forName("chisel-ns"))
    val queenAnneContext=K8SContext(pigCluster,blueUser, Namespace.forName("saw-ns"))
    val contexts=Map("federal-context"->federalContext,"queen-anne-context"->queenAnneContext)
    
    val directlyConstructedConfig=Configuration(clusters,contexts,federalContext,users)
    directlyConstructedConfig.clusters mustEqual parsedFromStringConfig.clusters
    directlyConstructedConfig.contexts mustEqual parsedFromStringConfig.contexts
    directlyConstructedConfig.users mustEqual parsedFromStringConfig.users
    directlyConstructedConfig.currentContext mustEqual parsedFromStringConfig.currentContext
    
    directlyConstructedConfig mustEqual parsedFromStringConfig
  }

  "Parse EC private keys from kubeconfig file" >> {
    val ecConfigStr =  """
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUJnRENDQVNlZ0F3SUJBZ0lVVWFxMkJNMFhaazBVb001OENGRXh2aEk0TWp3d0NnWUlLb1pJemowRUF3SXcKSFRFYk1Ca0dBMVVFQXhNU1ZVTlFJRU5zYVdWdWRDQlNiMjkwSUVOQk1CNFhEVEU0TURNeE9URTFOVEF3TUZvWApEVEl6TURNeE9ERTFOVEF3TUZvd0hURWJNQmtHQTFVRUF4TVNWVU5RSUVOc2FXVnVkQ0JTYjI5MElFTkJNRmt3CkV3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFa3pNY2JrNFRNc3lVcWcyYklKL050c2hCemxWcDcrenQKZ0trVHdHbGdYb09rZ3l3ckNBaU1YWnk4SG96dFE2NXJ3dDV1bUI1S0xXL3hSUi9vNExPclNxTkZNRU13RGdZRApWUjBQQVFIL0JBUURBZ0VHTUJJR0ExVWRFd0VCL3dRSU1BWUJBZjhDQVFJd0hRWURWUjBPQkJZRUZKc2g0cTlvCkpZV09vMGsxdGJqQlpDbkM1eFdvTUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSURlMmpwR0ptWlNTL0tISGxmSnEKdnU5YXVzZCs5Nk5rR0g1SGFyWEN0azRtQWlCSnlUSUYyZk5aZ2xzZEc3USs0aG5TZ21EeEgzWUd0K0RjVzJiZwpiY0VlcFE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCi0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQpNSUlCYWpDQ0FSQ2dBd0lCQWdJVVpaTTJPUFQwbTQxRGZDczFMRm5wYnNhL3hZb3dDZ1lJS29aSXpqMEVBd0l3CkV6RVJNQThHQTFVRUF4TUljM2RoY20wdFkyRXdIaGNOTVRnd016RTVNVFUxTURBd1doY05Nemd3TXpFME1UVTEKTURBd1dqQVRNUkV3RHdZRFZRUURFd2h6ZDJGeWJTMWpZVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSApBMElBQk5zVUo1YnhvRWZuNVVXS21TQ3Zoc3NlcDdubkpPa1dLUFVLaXgzSnhvbzlNNHp1WUVCdkpFV0VacmJnCmJyVWNPMHZyM3BWemxBUm83TXJZbk1MS09TbWpRakJBTUE0R0ExVWREd0VCL3dRRUF3SUJCakFQQmdOVkhSTUIKQWY4RUJUQURBUUgvTUIwR0ExVWREZ1FXQkJTdGhPTHVMSXNXL2pPOHcwSjJYM3hDM0FVY1FEQUtCZ2dxaGtqTwpQUVFEQWdOSUFEQkZBaUVBOTQwcGJxREJ6aGorTXNIMlhDUWRpUnJVQkFmTzVkV0YrdWFaUElnOHBHOENJSFF5ClNRQjhFS2wzcmZPVnpSOS9mU3FINm9kYVZQQk1GK3lqWk5VYnhFREgKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    server: https://horse.org:4443
  name: horse-cluster
contexts:
- context:
    cluster: horse-cluster
    namespace: chisel-ns
    user: ec-user
  name: federal-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: ec-user
  user:
    client-certificate-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNCRENDQWF1Z0F3SUJBZ0lVSjBuWWwvU1UycWRmT2RUMUlGUW4rL2xhOU5vd0NnWUlLb1pJemowRUF3SXcKRXpFUk1BOEdBMVVFQXhNSWMzZGhjbTB0WTJFd0hoY05NVGd3TXpFNU1UY3hNREF3V2hjTk1qZ3dNekUyTVRjeApNREF3V2pCc01Ra3dCd1lEVlFRR0V3QXhDVEFIQmdOVkJBZ1RBREVKTUFjR0ExVUVCeE1BTVNnd0pnWURWUVFLCkV4OVBjbU5oT2lCMmIyTnROSEZpZDJadk1XdHNhalZ2Ykhwdk5UTnViR3R2TVE4d0RRWURWUVFMRXdaRGJHbGwKYm5ReERqQU1CZ05WQkFNVEJXRmtiV2x1TUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFQytUZAoydnJzbFI2dkpJQzlOSDNNbDZUV0dySVU5TkhsczEyakoybW1KTjZ5Q3ljdDA1eW9qaGMxN1JjWlRzVTZVQTdyCm55T1pkQmhwd0plRGlUTEk5S09CZ3pDQmdEQU9CZ05WSFE4QkFmOEVCQU1DQmFBd0V3WURWUjBsQkF3d0NnWUkKS3dZQkJRVUhBd0l3REFZRFZSMFRBUUgvQkFJd0FEQWRCZ05WSFE0RUZnUVVZbkd3ZVpXVjZ4Mkl2YlFEWi9IUQpvS1dpekZzd0h3WURWUjBqQkJnd0ZvQVVyWVRpN2l5TEZ2NHp2TU5DZGw5OFF0d0ZIRUF3Q3dZRFZSMFJCQVF3CkFvRUFNQW9HQ0NxR1NNNDlCQU1DQTBjQU1FUUNJQmlSMnA5RUJRUDc1TEVsTUtXcEplQTc3aFZSTzA1V2VZN3QKQ3BjM0cwMEJBaUI0Um5odjJvZFUxWXB1Y25aMjNmWGFHTXN2aS9BaVhyekViOFE4M2lSeFNnPT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    client-key-data: LS0tLS1CRUdJTiBFQyBQUklWQVRFIEtFWS0tLS0tCk1IY0NBUUVFSUpPVDFJK3p0SVc4bEhKYVVkZFBnSEljTU1YK21abjMzNnRtOXdFRVhGelNvQW9HQ0NxR1NNNDkKQXdFSG9VUURRZ0FFQytUZDJ2cnNsUjZ2SklDOU5IM01sNlRXR3JJVTlOSGxzMTJqSjJtbUpONnlDeWN0MDV5bwpqaGMxN1JjWlRzVTZVQTdybnlPWmRCaHB3SmVEaVRMSTlBPT0KLS0tLS1FTkQgRUMgUFJJVkFURSBLRVktLS0tLQo=
"""
    val is = new java.io.ByteArrayInputStream(ecConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
    Try(k8sInit(k8sConfig)) must beSuccessfulTry
  }

  "Parse RSA private keys from kubeconfig file" >> {
    val ecConfigStr =  """
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUJnRENDQVNlZ0F3SUJBZ0lVVWFxMkJNMFhaazBVb001OENGRXh2aEk0TWp3d0NnWUlLb1pJemowRUF3SXcKSFRFYk1Ca0dBMVVFQXhNU1ZVTlFJRU5zYVdWdWRDQlNiMjkwSUVOQk1CNFhEVEU0TURNeE9URTFOVEF3TUZvWApEVEl6TURNeE9ERTFOVEF3TUZvd0hURWJNQmtHQTFVRUF4TVNWVU5RSUVOc2FXVnVkQ0JTYjI5MElFTkJNRmt3CkV3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFa3pNY2JrNFRNc3lVcWcyYklKL050c2hCemxWcDcrenQKZ0trVHdHbGdYb09rZ3l3ckNBaU1YWnk4SG96dFE2NXJ3dDV1bUI1S0xXL3hSUi9vNExPclNxTkZNRU13RGdZRApWUjBQQVFIL0JBUURBZ0VHTUJJR0ExVWRFd0VCL3dRSU1BWUJBZjhDQVFJd0hRWURWUjBPQkJZRUZKc2g0cTlvCkpZV09vMGsxdGJqQlpDbkM1eFdvTUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSURlMmpwR0ptWlNTL0tISGxmSnEKdnU5YXVzZCs5Nk5rR0g1SGFyWEN0azRtQWlCSnlUSUYyZk5aZ2xzZEc3USs0aG5TZ21EeEgzWUd0K0RjVzJiZwpiY0VlcFE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCi0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQpNSUlCYWpDQ0FSQ2dBd0lCQWdJVVpaTTJPUFQwbTQxRGZDczFMRm5wYnNhL3hZb3dDZ1lJS29aSXpqMEVBd0l3CkV6RVJNQThHQTFVRUF4TUljM2RoY20wdFkyRXdIaGNOTVRnd016RTVNVFUxTURBd1doY05Nemd3TXpFME1UVTEKTURBd1dqQVRNUkV3RHdZRFZRUURFd2h6ZDJGeWJTMWpZVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSApBMElBQk5zVUo1YnhvRWZuNVVXS21TQ3Zoc3NlcDdubkpPa1dLUFVLaXgzSnhvbzlNNHp1WUVCdkpFV0VacmJnCmJyVWNPMHZyM3BWemxBUm83TXJZbk1MS09TbWpRakJBTUE0R0ExVWREd0VCL3dRRUF3SUJCakFQQmdOVkhSTUIKQWY4RUJUQURBUUgvTUIwR0ExVWREZ1FXQkJTdGhPTHVMSXNXL2pPOHcwSjJYM3hDM0FVY1FEQUtCZ2dxaGtqTwpQUVFEQWdOSUFEQkZBaUVBOTQwcGJxREJ6aGorTXNIMlhDUWRpUnJVQkFmTzVkV0YrdWFaUElnOHBHOENJSFF5ClNRQjhFS2wzcmZPVnpSOS9mU3FINm9kYVZQQk1GK3lqWk5VYnhFREgKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    server: https://horse.org:4443
  name: horse-cluster
contexts:
- context:
    cluster: horse-cluster
    namespace: chisel-ns
    user: rsa-user
  name: federal-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: rsa-user
  user:
    client-certificate-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNTRENDQVRDZ0F3SUJBZ0lJRlJwWFNVQ0VkSTh3RFFZSktvWklodmNOQVFFTEJRQXdGVEVUTUJFR0ExVUUKQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB4T0RBek1Ea3hPVEk1TlRaYUZ3MHhPVEF6TURreE9USTVOVFphTURNeApGREFTQmdOVkJBb1RDMFJ2WTJ0bGNpQkpibU11TVJzd0dRWURWUVFERXhKa2IyTnJaWEl0Wm05eUxXUmxjMnQwCmIzQXdnWjh3RFFZSktvWklodmNOQVFFQkJRQURnWTBBTUlHSkFvR0JBTkZFRnRKT3VLS045VmtRKzJ5V0Z6d08KQUJPZ2hRM3lpSExBUkpQOHBxWHRDQ3VUV05weHdiUnM5TjlQcnhTbjBCblZzeXlreGlRNk12cHpLOWtDeWxBTgovWDZPbzFqWXgvK1BYdHp1NDAxc3VwbkhzSXI5S1VNQXhHVEdOK0NieXlRL3ZwTDlNSnVEV1VLUU1HYUtjNkFTCk5OdkEwVUVNWENQSTQrMHN0ZlFCQWdNQkFBR2pBakFBTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFBOFdtNk4KdWk1cC9URlBURHRsczRpdm93cWlhbTR2MVM5aTVtMitSQXBCRUZralpXek0xVDhRZ2dUc1FsdDY2cGhYR0h2VwphenBKYzd3ajQzN082aURnQ0UwdXFiYmQ3bGRPNk1vb1Z6azNTaE5rU2YrUVNQd3dRdzlBQlRNR01JcC9qYzRFClk1S0Y1dG5iQTl6b3RTWUpid1JaVG1JQUVTSVQydFhKaWlyUFBLTXI3ekhTVkNpZVJWM1JmMWUwNFBCb3JnOUoKLzVoZGVzNDRUWEdiSSt3OURqaHV2ZGhRN0h2REdsdjZ6MmpsSy9hYXNxQXNoalFtVW9Hd0REelBsbGdkUm5adgp2cWd2WnovSVZNcVY5eEEzb2ZDOUwxUGF1ekFGdExjNHVZTFhFa1JsR2dFcVA2N2RjbVlxZFJWQXA4WkVBLzVqCk05aXFNdk11NnN5c3hTQWEKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    client-key-data: LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlDWEFJQkFBS0JnUURSUkJiU1RyaWlqZlZaRVB0c2xoYzhEZ0FUb0lVTjhvaHl3RVNUL0thbDdRZ3JrMWphCmNjRzBiUFRmVDY4VXA5QVoxYk1zcE1Za09qTDZjeXZaQXNwUURmMStqcU5ZMk1mL2oxN2M3dU5OYkxxWng3Q0sKL1NsREFNUmt4amZnbThza1A3NlMvVENiZzFsQ2tEQm1pbk9nRWpUYndORkJERndqeU9QdExMWDBBUUlEQVFBQgpBb0dBU1daVGh1S2J1bENHalAzNjRpUm04K2FKT2xra01qY3VpdWxMWklqS3Z3bzd3bVVGVm1GdUt1WElvZ2NtCkJ0MnhqVTQ2Y1Y4K0xIakpaclU4M1Bvd2tXOHQycVE5aFdhZkdlbVY0bWhuYjFEYWNnRlNPMjZscytFT0NzODQKTDJONHR6UnpmTVFYZHd1cG56U1RCNjRsV1hDbjN3WS9kVm0yQUg5QlN2NVY2cDBDUVFEYmZMSlh1ZWlNRU1XOAptcE5zMVJBejE2MmhmWUxHVU5idFhCSk5xcm8xUTRtUS81enlCbWovUGFnSTROWWh3amxmSWZBbUVQcW5uanVHCmlqM0lKV29MQWtFQTlCUWE4R0dURnRkK2djczVzNmtOejFpbWdMR2FrRGRWcStHZitzaktvejc1WFg3c0tLeXkKTE1uVzE4ZzlKaGhSL3d1UzJzVlFNU1EwK2l6dld1NnRvd0pCQUo0M1V5L2R1WDVPRU53VjZUUEltcmRrUDZ0cgptRHR3eHAydmd4b3RlYkV2a0JqUHljakZTaWJEd1Q4MUkrYU41V0ZvUzM2Rk9zcGRTN2QrSzI3OVdXVUNRR2RpCjNNWlZqbWh1ZnplYlRhVzhSZzArRDhrVGNkVUVtMVZqRE5DOW5KZnBaTmNsbkFMZW85bzA1THdpSlVTdHFJM1AKNlRTaHY0WVJRQjk0U1NyTFR1RUNRQXRKOVYvVUg3WTl4cTlkd3JzVkZTM2FFTlFDTitsQThWQjBjcWZOSDlpUgpORFlZRTdGblJQV244VmlNdndsT3NzNmVUTjhIWGRrbXo2Yy8vV0NycENNPQotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQo=
"""
    val is = new java.io.ByteArrayInputStream(ecConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
    Try(k8sInit(k8sConfig)) must beSuccessfulTry
  }

  "Parse PKCS#8 private keys from kubeconfig file" >> {
    val pkcs8str =  """
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUJnRENDQVNlZ0F3SUJBZ0lVVWFxMkJNMFhaazBVb001OENGRXh2aEk0TWp3d0NnWUlLb1pJemowRUF3SXcKSFRFYk1Ca0dBMVVFQXhNU1ZVTlFJRU5zYVdWdWRDQlNiMjkwSUVOQk1CNFhEVEU0TURNeE9URTFOVEF3TUZvWApEVEl6TURNeE9ERTFOVEF3TUZvd0hURWJNQmtHQTFVRUF4TVNWVU5RSUVOc2FXVnVkQ0JTYjI5MElFTkJNRmt3CkV3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFa3pNY2JrNFRNc3lVcWcyYklKL050c2hCemxWcDcrenQKZ0trVHdHbGdYb09rZ3l3ckNBaU1YWnk4SG96dFE2NXJ3dDV1bUI1S0xXL3hSUi9vNExPclNxTkZNRU13RGdZRApWUjBQQVFIL0JBUURBZ0VHTUJJR0ExVWRFd0VCL3dRSU1BWUJBZjhDQVFJd0hRWURWUjBPQkJZRUZKc2g0cTlvCkpZV09vMGsxdGJqQlpDbkM1eFdvTUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSURlMmpwR0ptWlNTL0tISGxmSnEKdnU5YXVzZCs5Nk5rR0g1SGFyWEN0azRtQWlCSnlUSUYyZk5aZ2xzZEc3USs0aG5TZ21EeEgzWUd0K0RjVzJiZwpiY0VlcFE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCi0tLS0tQkVHSU4gQ0VSVElGSUNBVEUtLS0tLQpNSUlCYWpDQ0FSQ2dBd0lCQWdJVVpaTTJPUFQwbTQxRGZDczFMRm5wYnNhL3hZb3dDZ1lJS29aSXpqMEVBd0l3CkV6RVJNQThHQTFVRUF4TUljM2RoY20wdFkyRXdIaGNOTVRnd016RTVNVFUxTURBd1doY05Nemd3TXpFME1UVTEKTURBd1dqQVRNUkV3RHdZRFZRUURFd2h6ZDJGeWJTMWpZVEJaTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSApBMElBQk5zVUo1YnhvRWZuNVVXS21TQ3Zoc3NlcDdubkpPa1dLUFVLaXgzSnhvbzlNNHp1WUVCdkpFV0VacmJnCmJyVWNPMHZyM3BWemxBUm83TXJZbk1MS09TbWpRakJBTUE0R0ExVWREd0VCL3dRRUF3SUJCakFQQmdOVkhSTUIKQWY4RUJUQURBUUgvTUIwR0ExVWREZ1FXQkJTdGhPTHVMSXNXL2pPOHcwSjJYM3hDM0FVY1FEQUtCZ2dxaGtqTwpQUVFEQWdOSUFEQkZBaUVBOTQwcGJxREJ6aGorTXNIMlhDUWRpUnJVQkFmTzVkV0YrdWFaUElnOHBHOENJSFF5ClNRQjhFS2wzcmZPVnpSOS9mU3FINm9kYVZQQk1GK3lqWk5VYnhFREgKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    server: https://horse.org:4443
  name: horse-cluster
contexts:
- context:
    cluster: horse-cluster
    namespace: chisel-ns
    user: rsa-user
  name: federal-context
current-context: federal-context
kind: Config
preferences:
  colors: true
users:
- name: rsa-user
  user:
    client-certificate-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNTRENDQVRDZ0F3SUJBZ0lJRlJwWFNVQ0VkSTh3RFFZSktvWklodmNOQVFFTEJRQXdGVEVUTUJFR0ExVUUKQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB4T0RBek1Ea3hPVEk1TlRaYUZ3MHhPVEF6TURreE9USTVOVFphTURNeApGREFTQmdOVkJBb1RDMFJ2WTJ0bGNpQkpibU11TVJzd0dRWURWUVFERXhKa2IyTnJaWEl0Wm05eUxXUmxjMnQwCmIzQXdnWjh3RFFZSktvWklodmNOQVFFQkJRQURnWTBBTUlHSkFvR0JBTkZFRnRKT3VLS045VmtRKzJ5V0Z6d08KQUJPZ2hRM3lpSExBUkpQOHBxWHRDQ3VUV05weHdiUnM5TjlQcnhTbjBCblZzeXlreGlRNk12cHpLOWtDeWxBTgovWDZPbzFqWXgvK1BYdHp1NDAxc3VwbkhzSXI5S1VNQXhHVEdOK0NieXlRL3ZwTDlNSnVEV1VLUU1HYUtjNkFTCk5OdkEwVUVNWENQSTQrMHN0ZlFCQWdNQkFBR2pBakFBTUEwR0NTcUdTSWIzRFFFQkN3VUFBNElCQVFBOFdtNk4KdWk1cC9URlBURHRsczRpdm93cWlhbTR2MVM5aTVtMitSQXBCRUZralpXek0xVDhRZ2dUc1FsdDY2cGhYR0h2VwphenBKYzd3ajQzN082aURnQ0UwdXFiYmQ3bGRPNk1vb1Z6azNTaE5rU2YrUVNQd3dRdzlBQlRNR01JcC9qYzRFClk1S0Y1dG5iQTl6b3RTWUpid1JaVG1JQUVTSVQydFhKaWlyUFBLTXI3ekhTVkNpZVJWM1JmMWUwNFBCb3JnOUoKLzVoZGVzNDRUWEdiSSt3OURqaHV2ZGhRN0h2REdsdjZ6MmpsSy9hYXNxQXNoalFtVW9Hd0REelBsbGdkUm5adgp2cWd2WnovSVZNcVY5eEEzb2ZDOUwxUGF1ekFGdExjNHVZTFhFa1JsR2dFcVA2N2RjbVlxZFJWQXA4WkVBLzVqCk05aXFNdk11NnN5c3hTQWEKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=
    client-key-data: LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JR0hBZ0VBTUJNR0J5cUdTTTQ5QWdFR0NDcUdTTTQ5QXdFSEJHMHdhd0lCQVFRZ2s1UFVqN08waGJ5VWNscFIKMTArQWNod3d4ZjZabWZmZnEyYjNBUVJjWE5LaFJBTkNBQVFMNU4zYSt1eVZIcThrZ0wwMGZjeVhwTllhc2hUMAowZVd6WGFNbmFhWWszcklMSnkzVG5LaU9Gelh0RnhsT3hUcFFEdXVmSTVsMEdHbkFsNE9KTXNqMAotLS0tLUVORCBQUklWQVRFIEtFWS0tLS0tCg==
"""
    val is = new java.io.ByteArrayInputStream(pkcs8str.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is).get
    Try(k8sInit(k8sConfig)) must beSuccessfulTry
  }

  "If kubeconfig is not found at expected path then a Failure is returned" >> {
      import java.nio.file.Paths
      val path=Paths.get("file:///doesNotExist")
      val parsed = Configuration.parseKubeconfigFile(path)
      parsed.isFailure mustEqual true
  }

  "if a relative path and directory are specified, then the parsed config must contain the fully expanded paths" >> {
    val is = new java.io.ByteArrayInputStream(kubeConfigStr.getBytes(java.nio.charset.Charset.forName("UTF-8")))
    val k8sConfig = K8SConfiguration.parseKubeconfigStream(is, Some(Paths.get("/top/level/path")))
    val parsedFromStringConfig = k8sConfig.get
    val clientCertificate = parsedFromStringConfig.users("green-user").asInstanceOf[CertAuth].clientCertificate
    clientCertificate mustEqual Left("/top/level/path/path/to/my/client/cert")
  }

}
