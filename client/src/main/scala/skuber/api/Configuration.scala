package skuber.api

import java.io.File

import scala.collection.JavaConverters._
import scala.util.Try
import java.util.Base64

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

      type YamlMap= java.util.Map[String, Object]
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
            certificateAuthority=pathOrDataValueAt(clusterConfig, "certificate-authority","certificate-authority-data"))


        val k8sClusterMap = topLevelYamlToK8SConfigMap("cluster", toK8SCluster _)

        def toK8SAuthInfo(userConfig:YamlMap):AuthInfo = AuthInfo(
          clientCertificate = pathOrDataValueAt(userConfig, "client-certificate", "client-certificate-data"),
          clientKey = pathOrDataValueAt(userConfig, "client-key", "client-key-data"),
          jwt = userConfig.containsKey("auth-provider") match {
            case true => val authProvider = child(userConfig,"auth-provider"); authProvider.containsKey("config") match {
              case true => val config = child(authProvider,"config"); optionalValueAt(config,"id-token")
              case false => None
            }
            case false => None
          },
          token = optionalValueAt(userConfig, "token"),
          userName = optionalValueAt(userConfig, "username"),
          password = optionalValueAt(userConfig, "password"))

        val k8sAuthInfoMap = topLevelYamlToK8SConfigMap("user", toK8SAuthInfo _)

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
    * Uses the configuration from a running pod, mounted in /var/run/secrets/kubernetes.io/serviceaccount
    *
    * Code mostly from https://github.com/agemooij
    */
  lazy val useRunningPod: Try[Configuration] = Try {
    val rootK8sFolder = "/var/run/secrets/kubernetes.io/serviceaccount"
    val tokenFile = new File(rootK8sFolder + "/token")
    val namespaceFile = new File(rootK8sFolder + "/namespace")

    def fileContents(f: File): String = Source.fromFile(f, "UTF-8").getLines().mkString("\n")

    val cluster = Cluster(
      server = "https://kubernetes.default",
      certificateAuthority = Some(Left(rootK8sFolder + "/ca.crt"))
    )

    val authInfo = AuthInfo(token = Some(fileContents(tokenFile)))
    val namespace = Namespace.forName(fileContents(namespaceFile))
    val context = Context(cluster, authInfo, namespace)

    Configuration(
      clusters = Map("default" -> cluster),
      contexts = Map("default" -> context),
      currentContext = context
    )
  }
}
