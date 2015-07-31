package skuber.api

import java.time._
import java.time.format._

import skuber.model._
import skuber.model.Model._
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError

/**
 * @author David O'Riordan
 * Implicit Play json formatters for serializing Kubernetes objects to JSON
 * and vice versa 
 */
object JsonReadWrite {
  
  // Formatters for the Java 8 ZonedDateTime objects that represent
  // (ISO 8601 / RFC 3329 compatible) Kubernetes timestamp fields 
  implicit val timewWrites = Writes.temporalWrites[ZonedDateTime, DateTimeFormatter](
      DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  implicit val timeReads = Reads.DefaultZonedDateTimeReads    
      
  // Some fields can be omitted in the json if the value is "empty" e.g. empty lists or strings.
  // For these we use custom formatters to handle the empty/omitted cases.
  // Note: other types that may be empty/omitted are referenced using Option types with
  // None representing empty, as they don't have a specific empty value in the Scala representaion 
  // e.g. Timestamp, Spec and Status types.
  class MaybeEmpty(val path: JsPath) {
    def formatMaybeEmptyString(omitEmpty: Boolean=true): OFormat[String] =
      path.formatNullable[String].inmap[String](_.getOrElse(emptyS), s => if (omitEmpty && s.isEmpty) None else Some(s) )
      
    def formatMaybeEmptyList[T](implicit tReads: Reads[T], tWrites: OWrites[T], omitEmpty: Boolean=true) : OFormat[List[T]] =
      path.formatNullable[List[T]].inmap[List[T]](_.getOrElse(emptyL[T]), l => if (omitEmpty && l.isEmpty) None else Some(l))
      
    // Boolean: the empty value is 'false'  
    def formatMaybeEmptyBoolean(omitEmpty: Boolean=true) : OFormat[Boolean] =
      path.formatNullable[Boolean].inmap[Boolean](_.getOrElse(false), b => if (omitEmpty && !b) None else Some(b))
      
    // Int: the empty value is 0
    def formatMaybeEmptyInt(omitEmpty: Boolean=true) : OFormat[Int] =
      path.formatNullable[Int].inmap[Int](_.getOrElse(0), i => if (omitEmpty && i==0) None else Some(i))
  }
  // we make the above formatters available on JsPath objects via this implicit conversion
  implicit def jsPath2MaybeEmpty(path: JsPath) = new MaybeEmpty(path)
   
  implicit lazy val objFormat =
    (JsPath \ "kind").format[String] and
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "metadata").lazyFormat[ObjectMeta](objectMetaFormat) 
   // matadata format must be lazy as it can be used in indirectly recursive namespace structure (Namespace has a metadata.namespace field)
    
  implicit lazy val objectMetaFormat: Format[ObjectMeta] = (
    (JsPath \ "name").formatMaybeEmptyString() and
    (JsPath \ "generateName").formatMaybeEmptyString() and 
    (JsPath \ "namespace").formatMaybeEmptyString() and
    (JsPath \ "uid").formatMaybeEmptyString() and
    (JsPath \ "selfLink").formatMaybeEmptyString() and
    (JsPath \ "resourceVersion").formatMaybeEmptyString() and
    (JsPath \ "creationTimestamp").formatNullable[Timestamp] and
    (JsPath \ "deletionTimestamp").formatNullable[Timestamp] and
    (JsPath \ "labels").formatNullable[Map[String, String]] and
    (JsPath \ "annotations").formatNullable[Map[String, String]]
  )(ObjectMeta.apply _, unlift(ObjectMeta.unapply))
    
  implicit lazy val namespaceFormat : Format[Namespace] = (
      objFormat and
    	(JsPath \ "spec").formatNullable[Namespace.Spec] and
    	(JsPath \ "status").formatNullable[Namespace.Status]
    ) (Namespace.apply _, unlift(Namespace.unapply))	    
  
  implicit lazy val nsSpecFormat: Format[Namespace.Spec] = Json.format[Namespace.Spec]
  implicit lazy val nsStatusFormat: Format[Namespace.Status] = Json.format[Namespace.Status]
  implicit lazy val localObjRefFormat = Json.format[LocalObjectReference]
}