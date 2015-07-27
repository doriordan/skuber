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
 */
object JsonReadWrite {
  
  // Kubernetes time fields are formatted using RFC3329, a profile of ISO 8601 
  // Skuber uses Java 8 time package objects to represent times, here we use the default Play zoned 
  // time Json writes/reads formatters for them
  implicit val timewWites = Writes.DefaultZonedDateTimeWrites // not sure a writer is needed for clients
  implicit val timeReads = Reads.DefaultZonedDateTimeReads    
      
  // MaybeEmpty provides custom methods for formatting fields that may have "empty" values and
  // therefore be omitted by the sender in the json representation, per Kubernetes API spec.
  // Note this applies to Strings, Ints, Lists and Boolean types, whose empty values are legal and
  // non-null members of that type...fields that reference possibly "empty" object types - including Dates 
  // - on the other hand will be mapped using familiar formatNullable and Option types where None represents 
  // empty objects that can be omitted in the json.
  // The rationale for not using Option types for "emptyable" strings, lists and booleans is twofold,
  // (1) It would be confusing to be able to represent the empty value both by using None and Some(e)
  // where 'e' is the empty value
  // (2) Not using Option makes a lot of caller code more concise by not having to explicitly wrap values
  // using Some(x), unwrap using map etc. 
  
  class MaybeEmpty(val path: JsPath) {

    def formatMaybeEmptyString(omitEmpty: Boolean=true): OFormat[String] =
      path.formatNullable[String].inmap[String](_.getOrElse(emptyS), s => if (omitEmpty && s.isEmpty) None else Some(s) )
      
    def formatMaybeEmptyList[T](implicit tReads: Reads[T], tWrites: OWrites[T], omitEmpty: Boolean=true) : OFormat[List[T]] =
      path.formatNullable[List[T]].inmap[List[T]](_.getOrElse(emptyL[T]), l => if (omitEmpty && l.isEmpty) None else Some(l))
      
    // Boolean: the empty value is 'false'  
    def formatMayBeEmptyBoolean(omitEmpty: Boolean) : OFormat[Boolean] =
      path.formatNullable[Boolean].inmap[Boolean](_.getOrElse(false), b => if (omitEmpty && !b) None else Some(b))
      
    // Int: the empty value is 0
    def formatMayBeEmptyInt(omitEmpty: Boolean) : OFormat[Int] =
      path.formatNullable[Int].inmap[Int](_.getOrElse(0), i => if (omitEmpty && i==0) None else Some(i))
        
  }
  
  implicit def jsPath2MaybeEmpty(path: JsPath) = new MaybeEmpty(path)
   
  implicit lazy val objFormat =
    (JsPath \ "kind").format[String] and
    (JsPath \ "apiVersion").format[String] and
    (JsPath \ "metadata").lazyFormat[ObjectMeta](objectMetaFormat) 
   // matadata format must be lazy as it can be used in indirectly recursive namespace structure (namespace has metadata has namespace)
    
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
  
    implicit lazy val nsSpecReads : Reads[Namespace.Spec] =
      (JsPath \ "finalizers").read[List[String]].map(Namespace.Spec(_))

    implicit lazy val nsStatusReads : Reads[Namespace.Status] =
      (JsPath \ "phase").read[String].map(Namespace.Status(_))
    
    implicit lazy val nsSpecWrites : Writes[Namespace.Spec] =
      (JsPath \ "finalizers").write[List[String]].contramap(_.finalizers)

    implicit lazy val nsStatusWrites : Writes[Namespace.Status] =
      (JsPath \ "phase").write[String].contramap(_.phase)
      
      
}