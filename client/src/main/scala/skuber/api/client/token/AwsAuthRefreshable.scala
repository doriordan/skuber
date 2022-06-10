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
import scala.util.Try

final case class AwsAuthRefreshable(clusterName: Option[String] = None,
                                    region: Option[Regions] = None,
                                    refreshInterval: Duration = 60.minutes) extends AuthProviderAuth {

  @volatile private var cachedToken: Option[RefreshableToken] = None

  private val clusterNameToRefresh: String = {
    clusterName.getOrElse {
      defaultK8sConfig.currentContext.cluster.clusterName.getOrElse {
        throw new K8SException(Status(reason =
          Some("Cluster name not found, please provide an EKS (AWS) cluster name with AwsAuthRefreshable." +
            "alternatively cluster name can be identified from .kube/config"))
        )
      }
    }
  }

  // Try to parse region from cluster name
  private val regionToRefresh: Regions = {
    region.getOrElse {
      val region: Option[String] = clusterNameToRefresh.split("/").headOption.flatMap(_.split(":").lift(4))
      region.flatMap { rg =>
        Try(Regions.fromName(rg)).toOption
      }.getOrElse {
        throw new K8SException(Status(reason =
          Some("Region name not found, please provide an EKS (AWS) region name with AwsAuthRefreshable.")))
      }
    }
  }

  private def refreshGcpToken(): RefreshableToken = {
    val token = generateAwsToken
    val refreshedToken = RefreshableToken(token, DateTime.now.plus(refreshInterval.toSeconds))
    cachedToken = Some(refreshedToken)
    refreshedToken
  }

  private def generateAwsToken: String = {
    try {
      val credentialsProvider = new AWSStaticCredentialsProvider(new DefaultAWSCredentialsProviderChain().getCredentials)

      val tokenService: AWSSecurityTokenServiceClient = AWSSecurityTokenServiceClientBuilder
        .standard()
        .withRegion(regionToRefresh)
        .withCredentials(credentialsProvider)
        .build().asInstanceOf[AWSSecurityTokenServiceClient]

      val callerIdentityRequestDefaultRequest = new DefaultRequest[GetCallerIdentityRequest](new GetCallerIdentityRequest(), "sts")
      val uri = new URI("https", "sts.amazonaws.com", null, null)
      callerIdentityRequestDefaultRequest.setResourcePath("/")
      callerIdentityRequestDefaultRequest.setEndpoint(uri)
      callerIdentityRequestDefaultRequest.setHttpMethod(HttpMethodName.GET)
      callerIdentityRequestDefaultRequest.addParameter("Action", "GetCallerIdentity")
      callerIdentityRequestDefaultRequest.addParameter("Version", "2011-06-15")
      callerIdentityRequestDefaultRequest.addHeader("x-k8s-aws-id", clusterNameToRefresh)
      val signer = SignerFactory.createSigner(SignerFactory.VERSION_FOUR_SIGNER, new SignerParams("sts", regionToRefresh.getName))
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
    cachedToken match {
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
  def expired: Boolean =
    expiry.isBefore(System.currentTimeMillis)
}
