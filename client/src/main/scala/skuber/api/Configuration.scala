package skuber.api

import client._
import skuber.model.Namespace
import scala.util.{Try,Success,Failure}
import java.io.{File, FileInputStream}

import scala.collection.JavaConverters._
import org.yaml.snakeyaml.Yaml


/**
 * @author David O'Riordan
 */
case class Configuration(
      clusters: Map[String, K8SCluster] = Map(),
      contexts: Map[String, K8SContext] = Map(),  
      currentContext: K8SContext = K8SContext(),
      users: Map[String, K8SAuthInfo] = Map()) {
      
      def withCluster(name:String, cluster: K8SCluster) = this.copy(clusters = this.clusters + (name -> cluster))
      def withContext(name: String, context: K8SContext) = this.copy(contexts = this.contexts + (name -> context))
      def useContext(context: K8SContext) = this.copy(currentContext=context) 
}

object Configuration {

    // default config is suitable for use with kubectl proxy running on localhost:8001
    lazy val default = Configuration(
        clusters = Map("default" -> K8SCluster()), 
        contexts= Map("default" -> K8SContext()),
        currentContext = K8SContext())
        
    /**
     * Parse a kubeconfig file to get a K8S Configuration object for the API. 
     *
     * See https://github.com/kubernetes/kubernetes/blob/master/docs/user-guide/kubeconfig-file.md
     * for format of the kubeconfig file.   
     * Enables sharing of config with kubectl when skuber client is not simply directed via a kubectl proxy
     * However note that the merging funcionality described at the link above is not implemented
     * in the Skuber library.    
     * Also note that any certificate data in the config file is ignored - if a TLS connection is configured for 
     * a cluster, this will require the relevant certificate/key data to be installed into the Skuber client 
     * applications keystore / truststore (using keytool) per standard Java methods.
     */   
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
        
        def name(parent: YamlMap) = parent.get("name").asInstanceOf[String]  
        def child(parent: YamlMap, key: String) = parent.get(key).asInstanceOf[YamlMap]
        def topLevelList(key: String) = mainConfig.get(key).asInstanceOf[TopLevelYamlList]
        def valueAt[T](parent: YamlMap, key: String, fallback: Option[T] = None) : T = 
          parent.asScala.get(key).orElse(fallback).get.asInstanceOf[T]
        def optionalValueAt[T](parent: YamlMap, key: String) : Option[T] = parent.asScala.get(key).map(_.asInstanceOf[T])
        def topLevelYamlToK8SConfigMap[K8SConfigKind](kind: String, toK8SConfig: YamlMap=> K8SConfigKind) =
          topLevelList(kind + "s").asScala.map(item => name(item) -> toK8SConfig(child(item, kind))).toMap
          
        def toK8SCluster(clusterConfig: YamlMap) =
           K8SCluster(apiVersion=valueAt(clusterConfig, "api-version", Some("v1")),
                      server=valueAt(clusterConfig,"server",Some("http://localhost:8001")),
                      insecureSkipTLSVerify=valueAt(clusterConfig,"insecure-skip-tls-verify",Some(false)))   
        val k8sClusterMap = topLevelYamlToK8SConfigMap("cluster", toK8SCluster _)
              
        def toK8SAuthInfo(userConfig:YamlMap) =      
          K8SAuthInfo(token=optionalValueAt(userConfig, "token"),
                      userName=optionalValueAt(userConfig, "username"),
                      password=optionalValueAt(userConfig, "password"))
        val k8sAuthInfoMap = topLevelYamlToK8SConfigMap("user", toK8SAuthInfo _)                            
      
        def toK8SContext(contextConfig: YamlMap) = {
          val cluster=contextConfig.asScala.get("cluster").flatMap(clusterName => k8sClusterMap.get(clusterName.asInstanceOf[String])).get
          val authInfo =contextConfig.asScala.get("user").flatMap(userKey => k8sAuthInfoMap.get(userKey.asInstanceOf[String])).get
          val namespace=contextConfig.asScala.get("namespace").fold(Namespace.default) { name=>Namespace.forName(name.asInstanceOf[String]) }
          K8SContext(cluster,authInfo,namespace)    
        }       
        val k8sContextMap = topLevelYamlToK8SConfigMap("context", toK8SContext _)
        
        val currentContextStr: Option[String] = optionalValueAt(mainConfig, "current-context")
        val currentContext = currentContextStr.flatMap(k8sContextMap.get(_)).getOrElse(K8SContext())
        Configuration(k8sClusterMap, k8sContextMap, currentContext, k8sAuthInfoMap)
      }
    }    
}