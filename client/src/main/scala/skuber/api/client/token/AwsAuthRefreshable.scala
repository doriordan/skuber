package skuber.api.client.token

import java.net.URI
import java.util.{Base64, Date}
import com.amazonaws.DefaultRequest
import com.amazonaws.auth.presign.{PresignerFacade, PresignerParams}
import com.amazonaws.auth._
import com.amazonaws.http.HttpMethodName
import com.amazonaws.internal.auth.DefaultSignerProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceClient, AWSSecurityTokenServiceClientBuilder}
import org.joda.time.DateTime
import skuber.K8SException
import skuber.api.client._
import scala.concurrent.duration._

final case class AwsAuthRefreshable(clusterName: String,
                                    region: Regions,
                                    cachedAccessToken: Option[RefreshableToken] = None,
                                    refreshInterval: Duration = 60.minutes) extends AuthProviderAuth {

  @volatile private var refresh: Option[RefreshableToken] = cachedAccessToken.map(token => RefreshableToken(token.accessToken, token.expiry))

  private def refreshGcpToken(): RefreshableToken = {
    val token = generateAwsToken
    val refreshedToken = RefreshableToken(token, DateTime.now.plus(refreshInterval.toSeconds))
    refresh = Some(refreshedToken)
    refreshedToken
  }

  private def generateAwsToken: String = {
    try {
      val credentialsProvider = new AWSStaticCredentialsProvider(new DefaultAWSCredentialsProviderChain().getCredentials)

      val tokenService: AWSSecurityTokenServiceClient = AWSSecurityTokenServiceClientBuilder
        .standard()
        .withRegion(region)
        .withCredentials(credentialsProvider)
        .build().asInstanceOf[AWSSecurityTokenServiceClient]

      val callerIdentityRequestDefaultRequest = new DefaultRequest[GetCallerIdentityRequest](new GetCallerIdentityRequest(), "sts")
      val uri = new URI("https", "sts.amazonaws.com", null, null)
      callerIdentityRequestDefaultRequest.setResourcePath("/")
      callerIdentityRequestDefaultRequest.setEndpoint(uri)
      callerIdentityRequestDefaultRequest.setHttpMethod(HttpMethodName.GET)
      callerIdentityRequestDefaultRequest.addParameter("Action", "GetCallerIdentity")
      callerIdentityRequestDefaultRequest.addParameter("Version", "2011-06-15")
      callerIdentityRequestDefaultRequest.addHeader("x-k8s-aws-id", clusterName)
      val signer = SignerFactory.createSigner(SignerFactory.VERSION_FOUR_SIGNER, new SignerParams("sts", region.getName))
      val signerProvider = new DefaultSignerProvider(tokenService, signer)
      val presignerParams = new PresignerParams(
        uri,
        credentialsProvider,
        signerProvider,
        SdkClock.STANDARD)
      val presignerFacade = new PresignerFacade(presignerParams)
      val url = presignerFacade.presign(callerIdentityRequestDefaultRequest, new Date())
      val encodedUrl = Base64.getUrlEncoder.withoutPadding().encodeToString(url.toString.getBytes)

      s"k8s-aws-v1.$encodedUrl"
    } catch {
      case e: Exception =>
        throw new K8SException(Status(reason = Option(e.getMessage)))
    }
  }

  override def name: String = "aws"

  def accessToken: String = this.synchronized {
    refresh match {
      case Some(expired) if expired.expired =>
        refreshGcpToken().accessToken
      case None =>
        refreshGcpToken().accessToken
      case Some(token) =>
        token.accessToken
    }
  }

  override def toString: String = """AwsAuth(accessToken=<redacted>)""".stripMargin
}

final case class RefreshableToken(accessToken: String, expiry: DateTime) {
  def expired: Boolean = expiry.isBefore(System.currentTimeMillis)
}
