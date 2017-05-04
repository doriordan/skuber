
import java.net.URL
import java.util.Date

import play.api.libs.ws.ning.NingWSClient
import skuber.api.client.Context

import scala.collection.immutable.HashMap
import scala.language.implicitConversions

import scala.concurrent.ExecutionContext

/*
 * Represents core types and aliases 
 * @author David O'Riordan
 */
package object skuber {
  
  // define standard empty values - some Json formatters use them
  val emptyS=""
  val emptyB=false
  def emptyL[T]=List[T]()
  def emptyM[V]=Map[String,V]()
  
  def v1 = "v1"
  
  abstract class TypeMeta {
    def apiVersion: String = v1
    def kind: String 
  }
  
  type Timestamp=java.time.ZonedDateTime
  
  case class ObjectMeta(
    name: String = emptyS,
    generateName: String = emptyS,
    namespace: String = "default",
    uid: String = emptyS,
    selfLink: String = emptyS,
    resourceVersion: String = emptyS,
    creationTimestamp: Option[Timestamp] = None,
    deletionTimestamp: Option[Timestamp] = None,
    labels: Map[String, String] = Map(),
    annotations: Map[String, String] = Map(),
    generation: Int = 0)

  abstract class ObjectResource extends TypeMeta {
    def metadata: ObjectMeta
    def name = metadata.name
    def ns = if (metadata.namespace==emptyS) "default" else metadata.namespace
  }
 
  case class ListMeta( 
      selfLink: String = "",
      resourceVersion: String = "")
      
  case class APIVersions(
      kind: String,
      versions: List[String])
      
  // type for classes that can be items of some Kubernetes list type 
  // e.g. a Pod can be an item in a PodList, Node can be in a NodeList etc.
  // Just a type alias to ObjectResource 
  type KListItem=ObjectResource
   
  // base trait for all list kinds
  trait KList[K <: KListItem] extends TypeMeta {
    def metadata: Option[ListMeta]
    def items: List[K]
  }
  
  implicit def toList[I <: KListItem](resource: KList[I]) : List[I] = resource.items  
   
  case class PodList(
    val kind: String ="PodList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[Pod] = Nil) extends KList[Pod]
  
  case class NodeList(
    val kind: String ="NodeList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[Node] = Nil) extends KList[Node]
  
  case class ServiceList(
    val kind: String ="ServiceList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[Service] = Nil) extends KList[Service]
  
  case class EndpointList(
    val kind: String ="EndpointList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[Endpoints] = Nil) extends KList[Endpoints]
  
  case class EventList(
    val kind: String ="EventList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[Event] = Nil) extends KList[Event]
  
  case class ReplicationControllerList(  
    val kind: String ="ReplicationControllerList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[ReplicationController] = Nil) extends KList[ReplicationController]
  
  case class PersistentVolumeList(
    val kind: String ="PersistentVolumeList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[PersistentVolume] = Nil) extends KList[PersistentVolume]
  
   case class PersistentVolumeClaimList(
    val kind: String ="PersistentVolumeClaimList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta]= None,
    items: List[PersistentVolumeClaim] = Nil) extends KList[PersistentVolumeClaim]
  
   case class ServiceAccountList(
    val kind: String = "ServiceAccountList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta] = None,
    items: List[ServiceAccount] = Nil) extends KList[ServiceAccount]
  
   case class LimitRangeList(
    val kind: String = "LimitRangeList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta] = None,
    items: List[LimitRange] = Nil) extends KList[LimitRange]

  case class NamespaceList(
     val kind: String = "NamespaceList",
     override val apiVersion: String = v1,
     val metadata: Option[ListMeta] = None,
     items: List[Namespace] = Nil) extends KList[Namespace]

  case class ResourceQuotaList(
     val kind: String = "ResourceQuotaList",
     override val apiVersion: String = v1,
     val metadata: Option[ListMeta] = None,
     items: List[Resource.Quota] = Nil) extends KList[Resource.Quota]

   case class SecretList(
    val kind: String = "SecretList",
    override val apiVersion: String = v1,
    val metadata: Option[ListMeta] = None,
    items: List[Secret] = Nil) extends KList[Secret]
  
  type Finalizer=String
  type Phase=String
  
  trait Limitable // marker trait for types that can be subject to resource limits (i.e. Container, Pod)
  
  implicit def strToQuantity(value: String) : Resource.Quantity = Resource.Quantity(value)
  implicit def dblToQuantity(value: Double) : Resource.Quantity = Resource.Quantity((value * 1000).floor.toInt + "m")
  implicit def fltToQuantity(value: Float) : Resource.Quantity = Resource.Quantity((value * 1000).floor.toInt + "m")
  implicit def intToQuantity(value: Int) : Resource.Quantity = Resource.Quantity((value * 1000) + "m")

  case class LocalObjectReference(name: String)
  
  case class ObjectReference(
      kind: String = "",
      apiVersion: String = "",
      namespace: String = "",
      name: String = "",
      uid: String = "",
      resourceVersion: String = "",
      fieldPath: String = "") {
    def \(addPath: String) = this.copy(fieldPath=fieldPath + "/" + addPath)
  }
      
  implicit def objResourceToRef(obj: ObjectResource) = 
        ObjectReference(kind=obj.kind,
                        apiVersion=obj.apiVersion,
                        namespace=obj.ns,
                        name=obj.name,
                        uid = obj.metadata.uid,
                        resourceVersion = obj.metadata.resourceVersion)
      
  type IntOrString = Either[Int, String]                      
  type NameablePort = IntOrString // is either an integer or an IANA name       

  implicit def portNumToNameablePort(p:Int): NameablePort = Left(p)
  implicit def ianaNameToNameablePort(n: String): NameablePort = Right(n)
    
  sealed trait Handler // handlers are used by probes to get health check status from containers 
  
   // execute a command inside a container to check its health
  case class ExecAction(command: List[String]) extends Handler 
  
  // get health check status from a HTTP endpoint, returns non-OK HTTP status if health check fails
  case class HTTPGetAction(
      port: NameablePort, 
      host: String = "", 
      path: String = "", 
      schema: String = "HTTP") extends Handler {
    def url = {
      this.port match {
        case Left(p) => new URL(schema, host, p, path)
        case Right(p) => throw new Exception("Don't know how to create URL with a named port")   
      }
    }
  }
  
  object HTTPGetAction {
    def apply(i:Int) = new HTTPGetAction(Left(i))
    def apply(url: URL) = new HTTPGetAction(Left(url.getPort), url.getHost, url.getPath, url.getProtocol)
  }
  
  // TCP endpoint - health check succeeds if can connect to it
  case class TCPSocketAction(port: NameablePort) extends Handler
  
  case class Probe(action: Handler, initialDelaySeconds: Int = 0, timeoutSeconds: Int = 0)
  case class Lifecycle(postStart: Option[Handler] = None, preStop: Option[Handler] = None) 
  
  case class WatchedEvent(eventType: WatchedEventType.Value, eventObject: ObjectResource)
  object WatchedEventType extends Enumeration {
    type WatchedEventType = Value
    val ADDED,MODIFIED,DELETED,ERROR = Value
  }
  object DNSPolicy extends Enumeration {
     type DNSPolicy = Value
     val Default,ClusterFirst = Value
  }
   object RestartPolicy extends Enumeration {
     type RestartPolicy = Value
     val Always,OnFailure,Never = Value
  }
  
  object Protocol extends Enumeration {
    type Protocol = Value
    val TCP, UDP = Value
  }


  // aliases, references and delegates that enable using the API for many use cases without 
  // having to import anything from the skuber.api package
  val K8SCluster = skuber.api.client.Cluster
  val K8SContext = skuber.api.client.Context
  val K8SAuthInfo = skuber.api.client.AuthInfo
  type K8SRequestContext = skuber.api.client.RequestContext
  type K8SException = skuber.api.client.K8SException
  val K8SConfiguration = skuber.api.Configuration
  type K8SWatch[O] = skuber.api.Watch[O] 
  type K8SWatchEvent[I <: ObjectResource] = skuber.api.client.WatchEvent[I]
  
  def k8sInit(implicit executionContext: ExecutionContext)  = skuber.api.client.init
  def k8sInit(config: skuber.api.Configuration)(implicit executionContext : ExecutionContext) = skuber.api.client.init(config)
  def k8sInit(config: skuber.api.Configuration, httpClient: NingWSClient)(implicit executionContext : ExecutionContext) = skuber.api.client.init(config, httpClient)
  def createDefaultHttpClient(k8sContext: Context): NingWSClient = skuber.api.client.createDefaultHttpClient(k8sContext)

}
