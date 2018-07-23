package skuber.api

import java.io.File

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.Failure
import java.util.{Base64, Date}

import org.yaml.snakeyaml.Yaml
import skuber.Namespace
import skuber.api.client._

import scala.io.Source

/**
 * @author David O'Riordan
 */
case class Configuration(
      clusters: Map[String, Cluster] = Map(),
      contexts: Map[String, Context] = Map(),
      currentContext: Context = Context(),
      users: Map[String, AuthInfo] = Map()) {

      def withCluster(name:String, cluster: Cluster) = this.copy(clusters = this.clusters + (name -> cluster))
      def withContext(name: String, context: Context) = this.copy(contexts = this.contexts + (name -> context))
      def useContext(context: Context) = this.copy(currentContext=context)
      def setCurrentNamespace(namespaceName: String) =
        this.copy(currentContext=this.currentContext.copy(namespace=Namespace.forName(namespaceName)))

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

    def parseKubeconfigStream(is: java.io.InputStream, kubeconfigDir: Option[Path] = None) : Try[Configuration]= {

      type YamlMap = java.util.Map[String, Object]
      type TopLevelYamlList = java.util.List[YamlMap]

      Try {
        val yaml = new Yaml()
        val mainConfig = yaml.load(is).asInstanceOf[YamlMap]

        def name(parent: YamlMap) =
          parent.get("name").asInstanceOf[String]

        def child(parent: YamlMap, key: String) =
          parent.get(key).asInstanceOf[YamlMap]

        def topLevelList(key: String) =
          mainConfig.get(key).asInstanceOf[TopLevelYamlList]

        def valueAt[T](parent: YamlMap, key: String, fallback: Option[T] = None) : T =
          parent.asScala.get(key).orElse(fallback).get.asInstanceOf[T]

        def optionalValueAt[T](parent: YamlMap, key: String) : Option[T] =
          parent.asScala.get(key).map(_.asInstanceOf[T])

        def pathOrDataValueAt[T](parent: YamlMap, pathKey: String, dataKey: String) : Option[PathOrData] = {
          val path = optionalValueAt[String](parent, pathKey)
          val data = optionalValueAt[String](parent, dataKey)

          // Return some Right if data value is set, otherwise some Left if path value is set
          // if neither is set return None
          // Note - implication is that a data setting overrides a path setting
          (path, data) match {
            case (_, Some(b64EncodedData)) => Some(Right(Base64.getDecoder.decode(b64EncodedData)))
            case (Some(p), _) => {
              // path specified
              // if it is a relative path and a directory was specified then construct full path from those components,
              // otherwise just return path as given
              val expandedPath = (Paths.get(p), kubeconfigDir) match {
                case (basePath, Some(dir)) if !basePath.isAbsolute =>
                  Paths.get(dir.normalize.toString, basePath.normalize.toString).normalize.toString
                case _  => p
              }
              Some(Left(expandedPath))
            }
            case (None, None) => None
          }
        }

        def topLevelYamlToK8SConfigMap[K8SConfigKind](kind: String, toK8SConfig: YamlMap=> K8SConfigKind) =
          topLevelList(kind + "s").asScala.map(item => name(item) -> toK8SConfig(child(item, kind))).toMap


        def toK8SCluster(clusterConfig: YamlMap) =
          Cluster(
            apiVersion=valueAt(clusterConfig, "api-version", Some("v1")),
            server=valueAt(clusterConfig,"server",Some("http://localhost:8001")),
            insecureSkipTLSVerify=valueAt(clusterConfig,"insecure-skip-tls-verify",Some(false)),
            certificateAuthority=pathOrDataValueAt(clusterConfig, "certificate-authority","certificate-authority-data")
          )


        val k8sClusterMap = topLevelYamlToK8SConfigMap("cluster", toK8SCluster)

        def toK8SAuthInfo(userConfig:YamlMap): AuthInfo = {

          def authProviderRead(authProvider: YamlMap): Option[AuthProviderAuth] = {
            val config = child(authProvider, "config")
            name(authProvider).toLowerCase match {
              case "oidc" =>
                Some(OidcAuth(idToken = valueAt(config, "id-token")))
              case "gcp" =>
                Some(
                  GcpAuth(
                    accessToken = valueAt(config, "access-token"),
                    expiry = valueAt[Date](config, "expiry").toInstant,
                    cmdPath = valueAt(config, "cmd-path"),
                    cmdArgs = valueAt(config, "cmd-args")
                  )
                )
              case _ => None
            }
          }

          val maybeAuth = optionalValueAt[YamlMap](userConfig, "auth-provider") match {
            case Some(authProvider) => authProviderRead(authProvider)
            case None =>
              val clientCertificate = pathOrDataValueAt(userConfig, "client-certificate", "client-certificate-data")
              val clientKey = pathOrDataValueAt(userConfig, "client-key", "client-key-data")

              val token = optionalValueAt[String](userConfig, "token")

              val userName = optionalValueAt[String](userConfig, "username")
              val password = optionalValueAt[String](userConfig, "password")

              (userName, password, token, clientCertificate, clientKey) match {
                case (Some(u), Some(p), _, _, _) => Some(BasicAuth(u, p))
                case (_, _, Some(t), _, _) => Some(TokenAuth(t))
                case (u, _, _, Some(cert), Some(key)) => Some(CertAuth(cert, key, u))
                case _ => None
              }
          }

          maybeAuth.getOrElse(NoAuth)
        }
        val k8sAuthInfoMap = topLevelYamlToK8SConfigMap("user", toK8SAuthInfo)

        def toK8SContext(contextConfig: YamlMap) = {
          val cluster=contextConfig.asScala.get("cluster").flatMap(clusterName => k8sClusterMap.get(clusterName.asInstanceOf[String])).get
          val authInfo =contextConfig.asScala.get("user").flatMap(userKey => k8sAuthInfoMap.get(userKey.asInstanceOf[String])).get
          val namespace=contextConfig.asScala.get("namespace").fold(Namespace.default) { name=>Namespace.forName(name.asInstanceOf[String]) }
          Context(cluster,authInfo,namespace)
        }

        val k8sContextMap = topLevelYamlToK8SConfigMap("context", toK8SContext _)

        val currentContextStr: Option[String] = optionalValueAt(mainConfig, "current-context")
        val currentContext = currentContextStr.flatMap(k8sContextMap.get(_)).getOrElse(Context())

        Configuration(k8sClusterMap, k8sContextMap, currentContext, k8sAuthInfoMap)
      }
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

    lazy val maybeToken     = Try(Source.fromFile(tokenPath,     "utf-8").getLines().mkString("\n"))
    lazy val maybeNamespace = Try(Source.fromFile(namespacePath, "utf-8").getLines().mkString("\n"))

    // is not strictly required
    // but client-go tries to read ca.file and logs the following error if unable to and continues
    //"Expected to load root CA config from %s, but got err: %v", rootCAFile, err)
    lazy val ca: Option[PathOrData] = if (Files.exists(Paths.get(caPath))) Some(Left(caPath)) else None

    for {
      host      <- maybeHost
      port      <- maybePort
      token     <- maybeToken
      namespace <- maybeNamespace
      hostPort  = s"https://$host${if (port.length > 0) ":" + port else ""}"
      cluster   = Cluster(server = hostPort, certificateAuthority = ca)
      ctx       = Context(cluster, TokenAuth(token), Namespace.forName(namespace))
    } yield Configuration(
      clusters = Map("default" -> cluster),
      contexts = Map("default" -> ctx),
      currentContext = ctx
    )
  }
}
