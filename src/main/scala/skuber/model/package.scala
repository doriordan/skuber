package skuber.model


import java.net.URL
import java.util.Date
import scala.collection.immutable.HashMap

/*
 * Core representation of the Kubernetes V1 API model
 * @author David O'Riordan
 */
package object Model {

  sealed abstract class TypeMeta {
    def kind: String
    def apiVersion: String
  }
  
  case class ObjectMeta(
    name: String,
    namespace: Option[Namespace] = None,
    uid:Option[String] = None,
    generateName: Option[String] = None, 
    selfLink: Option[String] = None,
    resourceVersion: Option[String] = None,
    creationTimestamp: Option[Date] = None,
    deletionTimetsamp: Option[Date] = None,
    labels: Option[Map[String, String]] = None,
    annotations: Option[Map[String, String]] = None)
        
  abstract class ObjectResource extends TypeMeta {
    def metadata: ObjectMeta
  }

  case class ListMeta( 
      selfLink: Option[URL] = None,
      resourceVersion: Option[String] = None)
      
      
  // marker trait for any types that have a List resource type counterpart e.g. Pod, Node
  trait KListable 
  
  abstract class ListResource[T <: KListable] extends TypeMeta {
    def metadata: ListMeta
    def items: List[T]
  }    
 
  implicit def toList[T <: KListable](resource: ListResource[T]) : List[T] = resource.items

  type Finalizer=String
  type Phase=String
  
  case class LocalObjectReference(name: String)
  
  case class ObjectReference(
      kind: Option[String] = None,
      apiVersion: Option[String] = None,
      namespace: Option[String] = None,
      name: Option[String] = None,
      uid: Option[String] = None,
      resourceVersion: Option[String] = None,
      fieldPath: Option[String] = None)
      
  type NameablePort = Either[Int, String] // is either an int or an IANA name       

  sealed trait Handler
  case class ExecAction(command: List[String]) extends Handler
  case class HTTPGetAction(
      port: NameablePort, 
      host: Option[String] = None, 
      path: Option[String] = None, 
      schema: Option[String] = None) extends Handler
  case class TCPSocketAction(port: NameablePort) extends Handler
  
  case class Probe(action: Handler, initialDelaySeconds: Option[Int], timeoutSeconds: Option[Int])
  case class Lifecycle(postStart: Option[Handler], preStop: Option[Handler]) 

}