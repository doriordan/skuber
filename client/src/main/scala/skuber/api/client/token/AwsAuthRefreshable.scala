package skuber.api.client.token

import java.net.URI
import java.util.{Base64, Date}
import com.amazonaws.DefaultRequest
import com.amazonaws.auth._
import com.amazonaws.auth.presign.{PresignerFacade, PresignerParams}
import com.amazonaws.http.HttpMethodName
import com.amazonaws.internal.auth.DefaultSignerProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.{AWSSecurityTokenServiceClient, AWSSecurityTokenServiceClientBuilder}
import org.joda.time.DateTime
import skuber.K8SException
import skuber.api.client._
import scala.concurrent.duration._

final case class AwsAuthRefreshable(cluster: Option[Cluster] = None) extends AuthProviderRefreshableAuth {

  @volatile var cachedToken: Option[RefreshableToken] = None

  private val clusterName: String = {
    cluster.flatMap(_.clusterName).getOrElse {
      defaultK8sConfig.currentContext.cluster.clusterName.getOrElse {
        throw new K8SException(Status(reason =
          Some("Cluster name not found, please provide an EKS (AWS) cluster name within Cluster object")))
      }
    }
  }

  private val region: Option[Regions] = cluster.map(_.awsRegion).getOrElse(defaultK8sConfig.currentContext.cluster.awsRegion)


  //  https://github.com/kubernetes-sigs/aws-iam-authenticator/blob/27337b2b74c3140cf745a64f7154fe8ff7592258/pkg/token/token.go#L87
  // STS provides 15 minutes expiration, and it's not configurable.
  // Using 10 minutes to be on the safe side.
  private val refreshInterval = 10.minutes

  override def refreshToken: RefreshableToken = {
    val token = generateToken
    val refreshedToken = RefreshableToken(token, DateTime.now.plus(refreshInterval.toMillis))
    cachedToken = Some(refreshedToken)
    refreshedToken
  }

  override def generateToken: String = {
    try {
      val credentialsProvider = new AWSStaticCredentialsProvider(new DefaultAWSCredentialsProviderChain().getCredentials)
      val signer: Signer = SignerFactory.createSigner(SignerFactory.VERSION_FOUR_SIGNER,
        new SignerParams("sts", Regions.US_EAST_1.getName)) //it needs to be "us_east_1"

      val tokenService: AWSSecurityTokenServiceClient = AWSSecurityTokenServiceClientBuilder
        .standard()
        .withCredentials(credentialsProvider)
        .build().asInstanceOf[AWSSecurityTokenServiceClient]

      val callerIdentityRequestDefaultRequest = new DefaultRequest[GetCallerIdentityRequest](new GetCallerIdentityRequest(), "sts")

      val regionStr = region.map(rg => s".${rg.getName}").getOrElse("")
      val stsHost = s"sts$regionStr.amazonaws.com"

      val uri = new URI("https", stsHost, null, null)
      callerIdentityRequestDefaultRequest.setResourcePath("/")
      callerIdentityRequestDefaultRequest.setEndpoint(uri)
      callerIdentityRequestDefaultRequest.setHttpMethod(HttpMethodName.GET)
      callerIdentityRequestDefaultRequest.addParameter("Action", "GetCallerIdentity")
      callerIdentityRequestDefaultRequest.addParameter("Version", "2011-06-15")
      callerIdentityRequestDefaultRequest.addHeader("x-k8s-aws-id", clusterName)

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

  override def accessToken: String = this.synchronized {
    cachedToken match {
      case Some(token) if isTokenExpired(token) =>
        refreshToken.accessToken
      case None =>
        refreshToken.accessToken
      case Some(token) =>
        token.accessToken
    }
  }

  override def toString: String = """AwsAuth(accessToken=<redacted>)""".stripMargin

  override def isTokenExpired(refreshableToken: RefreshableToken): Boolean = refreshableToken.expiry.isBefore(System.currentTimeMillis)
}
