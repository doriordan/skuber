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
object KubernetesJson {

	val sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	implicit val rfc3339DateReads : Reads[Date] = Reads.of[String] map { sdf.parse(_) }

	implicit val nsSpecReads : Reads[Namespace.Spec] =
    	(JsPath	\ "finalizers").read[List[String]].map(Namespace.Spec(_))

    implicit val nsStatusReads : Reads[Namespace.Status] =
    	(JsPath	\ "phase").read[String].map(Namespace.Status(_))

	lazy val objReadsBuilder = 
		(JsPath \ "kind").read[String] and
		(JsPath \ "apiVersion").read[String] and
		(JsPath \ "metadata").read[ObjectMeta]

   	implicit lazy val namespaceReads : Reads[Namespace] = (
    	objReadsBuilder and
    	(JsPath \ "spec").readNullable[Namespace.Spec] and
    	(JsPath \ "status").readNullable[Namespace.Status]
    ) (Namespace.apply _)	
	
	implicit lazy val objectMetaReads: Reads[ObjectMeta] = (
		(JsPath \ "name").read[String] and
		(JsPath \ "namespace").readNullable[Namespace] and
		(JsPath \ "uid").readNullable[String] and
    	(JsPath \ "generateName").readNullable[String] and 
    	(JsPath \ "selfLink").readNullable[String] and
    	(JsPath \ "resourceVersion").readNullable[String] and
    	(JsPath \ "creationTimestamp").readNullable[Date] and
    	(JsPath \ "deletionTimestamp").readNullable[Date] and
    	(JsPath \ "labels").readNullable[Map[String, String]] and
    	(JsPath \ "annotations").readNullable[Map[String, String]]
    ) (ObjectMeta.apply _)
}