package skuber.api

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result

import scala.math.BigInt

import skuber.model._
import skuber.model.Model._
import ChoiceTypes._

import play.api.libs.json._


/**
 * @author David O'Riordan
 */
class ChoiceTypesSpec extends Specification {
  "This is a unit specification for the skuber json readers and writers for types that have multiple choices e.g. Volumes have multiple choices of Source type.\n ".txt
  
  
  // Volume reader and writer
  "A Volume can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the an emptydir type source" >> {
      val edVol = Volume("myVol", Volume.EmptyDir("myMedium"))
      val myVolJson = Json.toJson(edVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { case Volume.EmptyDir(medium) => medium mustEqual "myMedium"}
      readVol.source mustEqual Volume.EmptyDir("myMedium")
    }    
    "this can be done for the a hostpath type source" >> {
      val hpVol = Volume("myVol", Volume.HostPath("myHostPath"))
      val myVolJson = Json.toJson(hpVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { case Volume.HostPath(path) => path mustEqual "myHostPath"}
      readVol.source mustEqual Volume.HostPath("myHostPath")
    }   
    "this can be done for the a secret type source" >> {
      val scVol = Volume("myVol", Volume.Secret("mySecretName"))
      val myVolJson = Json.toJson(scVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { case Volume.Secret(secretName) => secretName mustEqual "mySecretName"}
      readVol.source mustEqual Volume.Secret("mySecretName")
    }    
  }    
}