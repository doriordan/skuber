package skuber.api

import java.net.URI
import scala.util.Try
import scala.util.Failure

import skuber.api.client._
import skuber.model.Namespace

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import scala.util.control.NonFatal

/**
 * @author David O'Riordan
 */
case class Configuration(
      clusters: Map[String, Cluster] = Map(),
      contexts: Map[String, Context] = Map(),
      currentContext: Context = Context(),
      users: Map[String, AuthInfo] = Map()) {

  def withCluster(name: String, cluster: Cluster): Configuration = this.copy(clusters = this.clusters + (name -> cluster))

  def withContext(name: String, context: Context): Configuration = this.copy(contexts = this.contexts + (name -> context))

  def useContext(context: Context): Configuration = this.copy(currentContext = context)

  def setCurrentNamespace(namespaceName: String): Configuration =
    this.copy(currentContext = this.currentContext.copy(namespace = Namespace.forName(namespaceName)))

}

object Configuration {

  // local proxy default config is suitable for use with kubectl proxy running on localhost:8080
  lazy val useLocalProxyDefault: Configuration = {
    val defaultCluster=Cluster()
    val defaultContext=Context(cluster=defaultCluster)
    Configuration(
      clusters = Map("default" -> defaultCluster),
      contexts= Map("default" -> defaultContext),
      currentContext = defaultContext)
  }

  // config to use a local proxy running on a specified port
  def useLocalProxyOnPort(port: Int): Configuration = {
    val clusterAddress=s"http://localhost:${port.toString}"
    val defaultCluster=Cluster(server = clusterAddress)
    val defaultContext=Context(cluster=defaultCluster)
    Configuration(
      clusters = Map("default" -> defaultCluster),
      contexts= Map("default" -> defaultContext),
      currentContext = defaultContext)
  }

  // config to use a proxy at specified address
  def useProxyAt(proxyAddress: String): Configuration = {
    val clusterAddress=proxyAddress
    val defaultCluster=Cluster(server = clusterAddress)
    val defaultContext=Context(cluster=defaultCluster)
    Configuration(
      clusters = Map("default" -> defaultCluster),
      contexts= Map("default" -> defaultContext),
      currentContext = defaultContext)
  }

  /**
     * Parse a kubeconfig file to get a K8S Configuration object for the API.
     *
     * See https://github.com/kubernetes/kubernetes/blob/master/docs/user-guide/kubeconfig-file.md
     * for format of the kubeconfig file.
     * Enables sharing of config with kubectl when skuber client is not simply directed via a kubectl proxy
     * However note that the merging functionality described at the link above is not implemented
     * in the Skuber library.
    **/
    import java.nio.file.{Files, Path, Paths}
    def parseKubeconfigFile(path: Path = Paths.get(System.getProperty("user.home"),".kube", "config")) : Try[Configuration] = {
      Try {
        val kubeconfigDir: Path = path.getParent
        val is = Files.newInputStream(path)
        (is,kubeconfigDir)
      } flatMap { case (is, kubeconfigDir) =>
        parseKubeconfigStream(is, Some(kubeconfigDir))
      }
    }

    def parseKubeconfigStream(is: java.io.InputStream, kubeconfigDir: Option[Path] = None) : Try[Configuration] =
      skuber.api.kubeconfig.KubeconfigConverter.parse(is, kubeconfigDir)

  private lazy val inClusterConfigReloadInterval: Option[FiniteDuration] = {
    import scala.concurrent.duration._
    // We default to 10 minutes because modern Kubernetes distributions issue service account tokens that are valid for
    // one hour, rotating them when they reach 80% of their lifespan. This means, any given arbitrary read of the token
    // from the filesystem can only be sure that that token will be valid for a maximum of 12 minutes (20% of 1 hour).
    // This is configurable, hence we allow it to be overridden by an environment variable.
    // Note that the equivalent Go code for this reloads every minute, erroneously stating in a comment that tokens are
    // rotated every 10 minutes. See https://github.com/kubernetes/client-go/blob/4f9edc15a7e71c3f9c7874a872a2545c8737726c/transport/token_source.go#L75-L79
    // If the value is less or equal to zero, then we don't reload.
    sys.env.get("SKUBER_TOKEN_RELOAD_INTERVAL_SECONDS")
      .map(_.toInt)
      .orElse(Some(600))
      .filter(_ > 0)
      .map(_.seconds)
  }

  /**
    * Tries to create in-cluster configuration using credentials mounted inside a running pod
    * <p>Follows official golang client logic
    *
    * @return Try[Configuration]
    *
    * @see https://kubernetes.io/docs/tasks/access-application-cluster/access-cluster/#accessing-the-api-from-a-pod
    *      https://github.com/kubernetes/client-go/blob/master/rest/config.go#L313
    *      https://github.com/kubernetes-client/java/blob/master/util/src/main/java/io/kubernetes/client/util/ClientBuilder.java#L134
    */
  lazy val inClusterConfig: Try[Configuration] = {

    val rootK8sFolder = "/var/run/secrets/kubernetes.io/serviceaccount"
    val tokenPath     = s"$rootK8sFolder/token"
    val namespacePath = s"$rootK8sFolder/namespace"
    val caPath        = s"$rootK8sFolder/ca.crt"

    lazy val maybeHost: Try[String] = Try(sys.env("KUBERNETES_SERVICE_HOST"))
      .recoverWith { case e: NoSuchElementException =>
        Failure(new Exception("environment variable KUBERNETES_SERVICE_HOST must be defined", e))}

    lazy val maybePort: Try[String] = Try(sys.env("KUBERNETES_SERVICE_PORT"))
      .recoverWith { case e: NoSuchElementException =>
        Failure(new Exception("environment variable KUBERNETES_SERVICE_PORT must be defined", e))}

    def tryLoadToken() = tryReadPath(tokenPath)

    lazy val maybeNamespace = tryReadPath(namespacePath)

    // is not strictly required
    // but client-go tries to read ca.file and logs the following error if unable to and continues
    //"Expected to load root CA config from %s, but got err: %v", rootCAFile, err)
    lazy val ca: Option[PathOrData] = if (Files.exists(Paths.get(caPath))) Some(Left(caPath)) else None

    for {
      host      <- maybeHost
      port      <- maybePort
      token     <- tryLoadToken()
      namespace <- maybeNamespace
    } yield {
      val hostPort = s"https://$host${if (port.nonEmpty) ":" + port else ""}"
      val cluster = Cluster(server = hostPort, certificateAuthority = ca)
      val auth = inClusterConfigReloadInterval match {
        case Some(reloadInterval) =>
          reloadableAccessTokenAuth { () =>
            Future.fromTry(tryLoadToken().map(t => (reloadInterval, t)))
          }
        case None =>
          TokenAuth(token)
      }
      val ctx = Context(cluster, auth, Namespace.forName(namespace))

      Configuration(
        clusters = Map("default" -> cluster),
        contexts = Map("default" -> ctx),
        currentContext = ctx
      )
    }
  }

  private def tryReadPath(path: String): Try[String] = Try {
    val source = Source.fromFile(path, "utf-8")
    try {
      source.getLines().mkString("\n")
    } finally {
      try {
        source.close()
      } catch {
        case NonFatal(_) =>
          // Ignore
      }
    }
  }

  /*
   * Get the current default configuration
   */
  def defaultK8sConfig: Configuration = {
    import java.nio.file.Paths

    val skuberUrlOverride = sys.env.get("SKUBER_URL")
    skuberUrlOverride match {
      case Some(url) =>
        Configuration.useProxyAt(url)
      case None =>
        val skuberConfigEnv = sys.env.get("SKUBER_CONFIG")
        skuberConfigEnv match {
          case Some(conf) if conf == "file" =>
            Configuration.parseKubeconfigFile().get // default kubeconfig location
          case Some(conf) if conf == "proxy" =>
            Configuration.useLocalProxyDefault
          case Some(fileUrl) =>
            val path = Paths.get(new URI(fileUrl))
            Configuration.parseKubeconfigFile(path).get
          case None =>
            // try KUBECONFIG
            val kubeConfigEnv = sys.env.get("KUBECONFIG")
            kubeConfigEnv.map { kc =>
              Configuration.parseKubeconfigFile(Paths.get(kc))
            }.getOrElse {
              // Try to get config from a running pod
              // if that is not set then use default kubeconfig location
              Configuration.inClusterConfig.orElse(
                Configuration.parseKubeconfigFile()
              )
            }.get
        }
    }
  }

}
