package skuber.api

import client._
import skuber.Namespace
import scala.util.{Try,Success,Failure}
import java.io.{File, FileInputStream}

import scala.collection.JavaConverters._
import org.yaml.snakeyaml.Yaml

import java.util.Base64

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
}

object Configuration {

    // default config is suitable for use with kubectl proxy running on localhost:8001
    lazy val default = Configuration(
        clusters = Map("default" -> Cluster()),
        contexts= Map("default" -> Context()),
        currentContext = Context())

    /**
     * Parse a kubeconfig file to get a K8S Configuration object for the API.
     *
     * See https://github.com/kubernetes/kubernetes/blob/master/docs/user-guide/kubeconfig-file.md
     * for format of the kubeconfig file.
     * Enables sharing of config with kubectl when skuber client is not simply directed via a kubectl proxy
     * However note that the merging functionality described at the link above is not implemented
     * in the Skuber library.
    **/
    import java.nio.file.{Path,Paths,Files}
    def parseKubeconfigFile(path: Path = Paths.get(System.getProperty("user.home"),".kube", "config")) : Try[Configuration] = {
       parseKubeconfigStream(Files.newInputStream(path))
    }

    def parseKubeconfigStream(is: java.io.InputStream) : Try[Configuration]= {

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
            case (Some(p), _) => Some(Left(p))
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

        def toK8SAuthInfo(userConfig:YamlMap) =
          AuthInfo(
            clientCertificate=pathOrDataValueAt(userConfig, "client-certificate","client-certificate-data"),
            clientKey=pathOrDataValueAt(userConfig, "client-key","client-key-data"),
            token=optionalValueAt(userConfig, "token"),
            userName=optionalValueAt(userConfig, "username"),
            password=optionalValueAt(userConfig, "password"))

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
}
