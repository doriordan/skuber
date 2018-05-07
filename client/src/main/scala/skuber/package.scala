
import scala.language.implicitConversions

import java.net.URL

import akka.stream.Materializer
import com.typesafe.config.Config
import skuber.api.client.RequestContext

/*
 * Represents core types and aliases 
 * @author David O'Riordan
 */
package object skuber {

  // define standard empty values - some Json formatters use them
  val emptyS = ""
  val emptyB = false

  def emptyL[T] = List[T]()

  def emptyM[V] = Map[String, V]()

  def v1 = "v1"

  abstract class TypeMeta {
    def apiVersion: String
    def kind: String
    def resourceVersion: String
  }

  type Timestamp = java.time.ZonedDateTime

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
    val metadata: ObjectMeta

    def name = metadata.name
    def resourceVersion=metadata.resourceVersion
    def ns = if (metadata.namespace == emptyS) "default" else metadata.namespace
  }

  // This will be used to associate an implicit API resource definition with each ObjectResource type
  // (the implicit values are declared in the companion objects of the resource type case classes)
  // This will allow us to implicitly pass the required spec to construct API requests with each
  // typed call to the Skuber API
  trait ResourceDefinition[T <: TypeMeta] {
    def spec: ResourceSpecification
  }

  // This trait is used to edit common fields (currently just metadata) of an object
  // Each object resource kind defines an implicit object of this type that can be passed around
  // Useful for methods that handle generic object resource kinds and need to modify some fields
  trait ObjectEditor[O <: ObjectResource] {
    def updateMetadata(obj: O, newMetadata: ObjectMeta): O
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
  type KListItem = ObjectResource

  // base trait for all list kinds
  sealed abstract class KList[K <: KListItem] extends TypeMeta {
    def metadata: Option[ListMeta]
    def items: List[K]
  }

  case class ListResource[K <: KListItem](
    override val apiVersion: String,
    override val kind: String,
    override val metadata: Option[ListMeta],
    override val items: List[K]) extends KList[K]
  {
    def resourceVersion=metadata.map(_.resourceVersion).getOrElse("")
    def itemNames: String = items.map { k => k.name } mkString(",")
  }

  implicit def toList[I <: KListItem](resource: KList[I]): List[I] = resource.items

  // Handy type aliases & implicits for core list resource kinds...
  // the resource definitions for each list kind is the same as that of the

  type PodList = ListResource[Pod]
  type PodTemplateList = ListResource[Pod.Template]
  type ConfigMapList = ListResource[ConfigMap]
  type NodeList = ListResource[Node]
  type ServiceList = ListResource[Service]
  type EndpointsList = ListResource[Endpoints]
  type EventList = ListResource[Event]
  type ReplicationControllerList = ListResource[ReplicationController]
  type PersistentVolumeList = ListResource[PersistentVolume]
  type PersistentVolumeClaimList = ListResource[PersistentVolumeClaim]
  type ServiceAccountList = ListResource[ServiceAccount]
  type LimitRangeList = ListResource[LimitRange]
  type NamespaceList = ListResource[Namespace]
  type ResourceQuotaList = ListResource[Resource.Quota]
  type SecretList = ListResource[Secret]

  def listResourceFromItems[K <: KListItem](items: List[K])(implicit rd: ResourceDefinition[K]) =
    new ListResource[K](
      apiVersion = rd.spec.group.map(_ + "/" + rd.spec.version).getOrElse(v1),
      kind = rd.spec.names.kind + "List",
      metadata = None,
      items = items
    )

  // a few functions for backwards compatibility (some the tests use them)
  def PodList(items: List[Pod]) = listResourceFromItems(items)
  def ServiceList(items: List[Service]) = listResourceFromItems(items)
  def ReplicationControllerList(items: List[ReplicationController]) = listResourceFromItems(items)


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
  
  case class Probe(
    action: Handler,
    initialDelaySeconds: Int = 0,
    timeoutSeconds: Int = 0,
    periodSeconds: Option[Int] = None,
    successThreshold: Option[Int] = None,
    failureThreshold: Option[Int] = None)

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

  // Delete options are (optionally) passed with a Delete request
  object DeletePropagation extends Enumeration {
    type DeletePropagation = Value
    val Orphan, Background, Foreground = Value
  }

  case class Preconditions(uid: String="")
  case class DeleteOptions(
    apiVersion: String = "v1",
    kind: String = "DeleteOptions",
    gracePeriodSeconds: Option[Int] = None,
    preconditions: Option[Preconditions] = None,
    propagationPolicy: Option[DeletePropagation.Value] = None)

  // aliases, references and delegates that enable using the API for many use cases without 
  // having to import anything from the skuber.api package
  val K8SCluster = skuber.api.client.Cluster
  val K8SContext = skuber.api.client.Context
  type K8SRequestContext = skuber.api.client.RequestContext
  type K8SException = skuber.api.client.K8SException
  val K8SConfiguration = skuber.api.Configuration
  type K8SWatchEvent[I <: ObjectResource] = skuber.api.client.WatchEvent[I]

  import akka.actor.ActorSystem

  /**
    * Initialise Skuber using default Kubernetes and application configuration.
    */
  def k8sInit(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    skuber.api.client.init
  }

  /**
    * Initialise Skuber using the specified Kubernetes configuration and default application configuration.
    */
  def k8sInit(config: skuber.api.Configuration)(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    skuber.api.client.init(config)
  }

  /**
    * Initialise Skuber using default Kubernetes configuration and the specified application configuration.
    */
  def k8sInit(appConfig: Config)(implicit actorSystem: ActorSystem, materializer: Materializer): RequestContext = {
    skuber.api.client.init(appConfig)
  }

  /**
    * Initialise Skuber using the specified Kubernetes and application configuration.
    */
  def k8sInit(config: skuber.api.Configuration, appConfig: Config)(implicit actorSystem: ActorSystem, materializer: Materializer)
      : RequestContext = {
    skuber.api.client.init(config, appConfig)
  }
}
