package skuber.api.kubeconfig

import java.io.InputStream
import java.nio.file.{Files, Path, Paths}
import java.time.{Duration => JDuration, Instant}
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import skuber.api.Configuration
import skuber.api.client._
import skuber.model.Namespace

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Converts the raw, spec-faithful [[RawConfig]] model (see [[KubeconfigModel]]) into skuber's runtime
 * `skuber.api.Configuration`/`skuber.api.client.{Cluster, Context, AuthInfo}` types.
 *
 * This is the sole entry point used by `skuber.api.Configuration.parseKubeconfigStream`.
 */
private[skuber] object KubeconfigConverter {

  // Bearer-token expiry skew, mirrors the existing GcpRefresh.expired buffer in
  // skuber.api.client.package.
  private val ExpirySkewSeconds = 20L

  // Used when an exec plugin's response doesn't include an expirationTimestamp - without one we
  // have no proactive-refresh signal at all, so fall back to a conservative revalidation interval
  // rather than caching the credential forever.
  private val DefaultCredentialValidity: FiniteDuration = 60.seconds

  // token/exec-plugin credential refreshes are blocking (subprocess exec, file reads) - give them
  // a small dedicated, idle-when-unused thread pool rather than risk blocking a caller's I/O
  // dispatcher thread. core has no Akka/Pekko dispatcher of its own to borrow.
  private val blockingThreadCount = new AtomicInteger(0)
  private val blockingExecutor = Executors.newCachedThreadPool { (r: Runnable) =>
    val t = new Thread(r, s"skuber-kubeconfig-credential-refresh-${blockingThreadCount.incrementAndGet()}")
    t.setDaemon(true)
    t
  }
  private implicit val blockingExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(blockingExecutor)

  def parse(is: InputStream, kubeconfigDir: Option[Path]): Try[Configuration] =
    KubeconfigLoader.load(is).map(raw => toConfiguration(raw, kubeconfigDir))

  private def toConfiguration(raw: RawConfig, kubeconfigDir: Option[Path]): Configuration = {
    val clusterMap: Map[String, Cluster] =
      raw.clusters.map(nc => nc.name -> toCluster(nc.cluster, kubeconfigDir)).toMap

    val rawClusterByName: Map[String, RawCluster] = raw.clusters.map(nc => nc.name -> nc.cluster).toMap
    val rawUserByName: Map[String, RawAuthInfo] = raw.users.map(nu => nu.name -> nu.user).toMap

    // The flat, top-level `users:` map has no associated cluster, so an exec plugin configured
    // with provideClusterInfo=true simply won't receive cluster info when its AuthInfo is looked
    // up this way. In practice only skuber.api.Configuration.contexts (and thus currentContext) is
    // ever used to actually drive HTTP requests - see toAuthInfo's cluster-bound rebuild below.
    val userMap: Map[String, AuthInfo] =
      raw.users.map(nu => nu.name -> toAuthInfo(nu.user, clusterForExec = None, kubeconfigDir)).toMap

    val contextMap: Map[String, Context] = raw.contexts.map { namedContext =>
      val rawContext = namedContext.context
      val clusterName = rawContext.cluster.filterNot(_.isEmpty)
      val userName = rawContext.user.filterNot(_.isEmpty)

      val boundCluster = clusterName.flatMap(clusterMap.get)
      val cluster = boundCluster.getOrElse(Cluster())

      val authInfo = userName.flatMap(rawUserByName.get) match {
        case Some(rawUser) => toAuthInfo(rawUser, clusterForExec = boundCluster, kubeconfigDir)
        case None => NoAuth
      }

      val namespace = rawContext.namespace.fold(Namespace.default)(Namespace.forName)

      namedContext.name -> Context(cluster, authInfo, namespace)
    }.toMap

    val currentContext = raw.currentContext.flatMap(contextMap.get).getOrElse(Context())

    Configuration(clusterMap, contextMap, currentContext, userMap)
  }

  private def toCluster(raw: RawCluster, kubeconfigDir: Option[Path]): Cluster =
    Cluster(
      apiVersion = raw.apiVersion.getOrElse("v1"),
      // Preserves a pre-existing quirk: the legacy parser's own fallback here ("...:8001") differs
      // from Cluster()'s own default server ("...:8080", skuber.api.client.defaultApiServerURL).
      server = raw.server.getOrElse("http://localhost:8001"),
      insecureSkipTLSVerify = raw.insecureSkipTLSVerify.getOrElse(false),
      certificateAuthority = pathOrData(raw.certificateAuthority, raw.certificateAuthorityData, kubeconfigDir)
    )

  private def toAuthInfo(raw: RawAuthInfo, clusterForExec: Option[Cluster], kubeconfigDir: Option[Path]): AuthInfo = {
    raw.exec match {
      case Some(exec) => execAuthInfo(exec, clusterForExec)
      case None =>
        raw.authProvider match {
          case Some(provider) => authProviderAuthInfo(provider)
          case None =>
            val clientCertificate = pathOrData(raw.clientCertificate, raw.clientCertificateData, kubeconfigDir)
            val clientKey = pathOrData(raw.clientKey, raw.clientKeyData, kubeconfigDir)
            (raw.username, raw.password, raw.token, raw.tokenFile, clientCertificate, clientKey) match {
              case (Some(u), Some(p), _, _, _, _) => BasicAuth(u, p)
              case (_, _, Some(t), _, _, _) => TokenAuth(t)
              case (_, _, None, Some(file), _, _) => tokenFileAuthInfo(file)
              case (u, _, _, _, Some(cert), Some(key)) => CertAuth(cert, key, u)
              case _ => NoAuth
            }
        }
    }
  }

  private def authProviderAuthInfo(provider: RawAuthProvider): AuthInfo = provider.name.toLowerCase match {
    case "oidc" =>
      OidcAuth(idToken = provider.idToken.getOrElse(sys.error("auth-provider 'oidc' config missing 'id-token'")))
    case "gcp" =>
      GcpAuth(
        accessToken = provider.accessToken,
        expiry = provider.expiry,
        cmdPath = provider.cmdPath.getOrElse(sys.error("auth-provider 'gcp' config missing 'cmd-path'")),
        cmdArgs = provider.cmdArgs.getOrElse(sys.error("auth-provider 'gcp' config missing 'cmd-args'"))
      )
    case _ => NoAuth
  }

  // Re-reads the token file on each reload, matching client-go's periodically-re-read-token-file
  // semantics, capped by DefaultCredentialValidity so we don't re-read on every single request.
  private def tokenFileAuthInfo(path: String): AuthInfo = {
    def reload(): Future[(FiniteDuration, String)] = Future {
      val token = new String(Files.readAllBytes(Paths.get(path)), "UTF-8").trim
      (DefaultCredentialValidity, token)
    }
    reloadableAccessTokenAuth(() => reload())
  }

  private def execAuthInfo(exec: RawExecConfig, clusterForExec: Option[Cluster]): AuthInfo = {
    def reload(): Future[(FiniteDuration, String)] = Future {
      val clusterInfo = if (exec.provideClusterInfo) clusterForExec.map(toExecClusterInfo) else None
      ExecCredentialRunner.run(exec, clusterInfo).get match {
        case ExecToken(token, expiresAt) =>
          val validity = expiresAt
            .map(expiry => JDuration.between(Instant.now, expiry.minusSeconds(ExpirySkewSeconds)))
            .map(d => (d.toMillis max 0L).millis)
            .getOrElse(DefaultCredentialValidity)
          (validity, token)
        case _: ExecClientCert =>
          throw new UnsupportedOperationException(
            s"credential plugin '${exec.command}' returned a client certificate, which skuber cannot yet use: " +
              "TLS client certificates are only established once, when the HTTP connection pool is built, and " +
              "cannot currently be refreshed from an exec plugin. This is a known limitation (tracked as " +
              "follow-up work), affecting e.g. Teleport's 'tsh kube credentials' plugin."
          )
      }
    }
    reloadableAccessTokenAuth(() => reload())
  }

  private def toExecClusterInfo(cluster: Cluster): ExecClusterInfo = {
    val certificateAuthorityDataBase64 = cluster.certificateAuthority.map {
      case Right(bytes) => Base64.getEncoder.encodeToString(bytes)
      case Left(path) => Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get(path)))
    }
    ExecClusterInfo(cluster.server, certificateAuthorityDataBase64, cluster.insecureSkipTLSVerify)
  }

  // Preserves the pre-existing precedence and relative-path-expansion behavior: an embedded *-data
  // value overrides a path, and a relative path is resolved against kubeconfigDir (the parsed
  // file's parent directory) when one was supplied.
  private def pathOrData(pathOpt: Option[String], dataOpt: Option[String], kubeconfigDir: Option[Path]): Option[PathOrData] =
    (pathOpt, dataOpt) match {
      case (_, Some(base64Data)) => Some(Right(Base64.getDecoder.decode(base64Data)))
      case (Some(p), _) =>
        val expandedPath = (Paths.get(p), kubeconfigDir) match {
          case (basePath, Some(dir)) if !basePath.isAbsolute =>
            Paths.get(dir.normalize.toString, basePath.normalize.toString).normalize.toString
          case _ => p
        }
        Some(Left(expandedPath))
      case (None, None) => None
    }
}
