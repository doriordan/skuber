package skuber.api

import java.util.Date
import java.text.SimpleDateFormat

import skuber.model._
import skuber.model.Model._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * @author David O'Riordan
 */
object JsonReadWrite {

	val sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	implicit val rfc3339DateReads : Reads[Date] = Reads.of[String] map { sdf.parse(_) }

	lazy val objReadsBuilder = 
		(JsPath \ "kind").read[String] and
		(JsPath \ "apiVersion").read[String] and
		(JsPath \ "metadata").read[ObjectMeta]
  
  lazy val objWritesBuilder = (
    (JsPath \ "kind").write[String] and
    (JsPath \ "apiVersion").write[String] and
    (JsPath \ "metadata").write[ObjectMeta]
  )

  implicit lazy val objectMetaReads: Reads[ObjectMeta] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "namespace").lazyReadNullable(namespaceReads) and
    (JsPath \ "uid").readNullable[String] and
    (JsPath \ "generateName").readNullable[String] and 
    (JsPath \ "selfLink").readNullable[String] and
    (JsPath \ "resourceVersion").readNullable[String] and
    (JsPath \ "creationTimestamp").readNullable[Date] and
    (JsPath \ "deletionTimestamp").readNullable[Date] and
    (JsPath \ "labels").readNullable[Map[String, String]] and
    (JsPath \ "annotations").readNullable[Map[String, String]]
  )(ObjectMeta.apply _)
    
  implicit lazy val objectMetaWrites: Writes[ObjectMeta] = (
    (JsPath \ "name").write[String] and
    (JsPath \ "namespace").lazyWriteNullable(namespaceWrites) and
    (JsPath \ "uid").writeNullable[String] and
      (JsPath \ "generateName").writeNullable[String] and 
      (JsPath \ "selfLink").writeNullable[String] and
      (JsPath \ "resourceVersion").writeNullable[String] and
      (JsPath \ "creationTimestamp").writeNullable[Date] and
      (JsPath \ "deletionTimestamp").writeNullable[Date] and
      (JsPath \ "labels").writeNullable[Map[String, String]] and
      (JsPath \ "annotations").writeNullable[Map[String, String]]
    ) (unlift(ObjectMeta.unapply))

   	implicit lazy val namespaceReads : Reads[Namespace] = (
    	objReadsBuilder and
    	(JsPath \ "spec").readNullable[Namespace.Spec] and
    	(JsPath \ "status").readNullable[Namespace.Status]
    ) (Namespace.apply _)	
	
    implicit lazy val nsSpecReads : Reads[Namespace.Spec] =
      (JsPath \ "finalizers").read[List[String]].map(Namespace.Spec(_))

    implicit lazy val nsStatusReads : Reads[Namespace.Status] =
      (JsPath \ "phase").read[String].map(Namespace.Status(_))
      
    implicit lazy val   namespaceWrites: Writes[Namespace] = (
        objWritesBuilder and
        (JsPath \ "spec").writeNullable[Namespace.Spec] and
        (JsPath \ "status").writeNullable[Namespace.Status]
    ) (unlift(Namespace.unapply))
    
    implicit lazy val nsSpecWrites : Writes[Namespace.Spec] =
      (JsPath \ "finalizers").write[List[String]].contramap(_.finalizers)

    implicit lazy val nsStatusWrites : Writes[Namespace.Status] =
      (JsPath \ "phase").write[String].contramap(_.phase)
      
      
}