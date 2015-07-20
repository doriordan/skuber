package skuber.api

import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * @author David O'Riordan
 */
// A ChoiceTypes class holds a set of json mappings for subtypes of a single base
// type, where 
// In some cases an object can contains a field that is basically a choice of one of
// a number of subtypes e.g. Volume source sub-types such as emptyDir, hostOath etc.

object ChoiceTypes {
  import skuber.model._
  import skuber.model.Model._
  
   // Volume Source types
   import skuber.model.Volume._
   implicit lazy val emptyDirReads : Reads[EmptyDir] =
     (JsPath \ "medium").read[String].map(EmptyDir(_))
   implicit lazy val emptyDirWrites: Writes[EmptyDir] =  
     (JsPath \ "medium").write[String].contramap(_.medium)
   implicit lazy val hostPathReads : Reads[HostPath] =
     (JsPath \ "path").read[String].map(HostPath(_))
   implicit lazy val hostPathWrites: Writes[HostPath] =  
     (JsPath \ "path").write[String].contramap(_.path)  
    implicit lazy val secretReads : Reads[Secret] =
     (JsPath \ "secretName").read[String].map(Secret(_))
   implicit lazy val secretWrites: Writes[Secret] =  
     (JsPath \ "secretName").write[String].contramap(_.secretName)  
  
   implicit lazy val volumeSourceReads: Reads[Source] = (
     (JsPath \ "emptyDir").read[EmptyDir].map(x => x: Source) |
     (JsPath \ "hostPath").read[HostPath].map(x => x: Source) |
     (JsPath \ "secret").read[Secret].map(x => x:Source)
   )
   
   implicit lazy val volumeSourceWrites: Writes[Source] = Writes[Source] { 
     source => source match {
       case ed: EmptyDir => (JsPath \ "emptyDir").write[EmptyDir](emptyDirWrites).writes(ed)
       case hp: HostPath => (JsPath \ "hostPath").write[HostPath](hostPathWrites).writes(hp)
       case secr: Secret => (JsPath \ "secret").write[Secret](secretWrites).writes(secr)
     }
   }
  
   implicit lazy val volumeReads: Reads[Volume] = (
     (JsPath \ "name").read[String] and
     volumeSourceReads
   )(Volume.apply _)
        
   implicit lazy val volumeWrites: Writes[Volume] = (
     (JsPath \ "name").write[String] and 
     JsPath.write[Source]
   )(unlift(Volume.unapply))
   
} 
  
