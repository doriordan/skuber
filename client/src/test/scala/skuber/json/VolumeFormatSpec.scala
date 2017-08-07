package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure

import scala.math.BigInt

import skuber._
import format._

import play.api.libs.json._


/**
 * @author David O'Riordan
 */
class VolumeReadWriteSpec extends Specification {
  "This is a unit specification for the skuber json readers and writers for types that have multiple choices e.g. Volumes have multiple choices of Source type.\n ".txt 
  
  // Volume reader and writer
  "A Volume spec can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the an emptydir type source spec" >> {
      val edVol = Volume("myVol", Volume.EmptyDir())
      val myVolJson = Json.toJson(edVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.EmptyDir(medium) => medium mustEqual Volume.DefaultStorageMedium
        case _ => Failure("not an emptyDir!")
      }
      readVol.source mustEqual Volume.EmptyDir()
    }    
    "this can be done for the a hostpath type source" >> {
      val hpVol = Volume("myVol", Volume.HostPath("myHostPath"))
      val myVolJson = Json.toJson(hpVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.HostPath(path) => path mustEqual "myHostPath"
        case _ => Failure("not a hostpath!")
      }
      readVol.source mustEqual Volume.HostPath("myHostPath")
    }   
    "this can be done for the a secret type source spec" >> {
      val scVol = Volume("myVol", Volume.Secret("mySecretName"))
      val myVolJson = Json.toJson(scVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.Secret(secretName, _) => secretName mustEqual "mySecretName"
        case _ => Failure("not a secret!")
      }
      readVol.source mustEqual Volume.Secret("mySecretName")
    }    
    "this can be done for the a git repo source spec" >> {
      val gitVol = Volume("myVol", Volume.GitRepo("git://host/mygitrepo"))
      val myVolJson = Json.toJson(gitVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.GitRepo(repoURL, revision) => repoURL mustEqual "git://host/mygitrepo"
        case _ => Failure("not a git repo!")
      }
      readVol.source mustEqual Volume.GitRepo("git://host/mygitrepo", None)
      
      val gitVol2 = Volume("myVol2", Volume.GitRepo("git://host/mygitrepo2", Some("abdcef457677")))
      val myVolJson2 = Json.toJson(gitVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name mustEqual "myVol2"
      readVol2.source match { 
        case Volume.GitRepo(repoURL, revision) => 
          repoURL mustEqual "git://host/mygitrepo2"
          revision mustEqual Some("abdcef457677")   
        case _ => Failure("not a git repo!") 
      }
      readVol2.source mustEqual Volume.GitRepo("git://host/mygitrepo2",Some("abdcef457677"))
    }    
    "this can be done for the a GCE Persistent Disk source spec" >> {
      val gceVol = Volume("myVol", Volume.GCEPersistentDisk("pd1","ext4",3))
      val myVolJson = Json.toJson(gceVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.GCEPersistentDisk(pdName,fsType,partition,readonly) => 
          pdName mustEqual "pd1"
          fsType mustEqual "ext4"
          partition mustEqual 3
          readonly mustEqual false
        case _ => Failure("not a GCE disk!")
      }   
      readVol.source mustEqual Volume.GCEPersistentDisk("pd1","ext4", 3)
      
      val gceVol2 = Volume("myVol", Volume.GCEPersistentDisk("pd1","ext4",readOnly = true))
      val myVolJson2 = Json.toJson(gceVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name mustEqual "myVol"
      readVol2.source match { 
        case Volume.GCEPersistentDisk(pdName,fsType,partition,readonly) => 
          pdName mustEqual "pd1"
          fsType mustEqual "ext4"
          partition mustEqual 0
          readonly mustEqual true
        case _ => Failure("not a GCE disk!")
      }   
      readVol2.source mustEqual Volume.GCEPersistentDisk("pd1","ext4",0, true)
    }    
    "this can be done for the a AWS EBS source spec" >> {
      val awsVol = Volume("myVol", Volume.AWSElasticBlockStore("vol1","ext4",3))
      val myVolJson = Json.toJson(awsVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      readVol.source match { 
        case Volume.AWSElasticBlockStore(volName,fsType,partition,readonly) => 
          volName mustEqual "vol1"
          fsType mustEqual "ext4"
          partition mustEqual 3
          readonly mustEqual false
        case _ => Failure("not an AWS EBS volume!")
      }   
      readVol.source mustEqual Volume.AWSElasticBlockStore("vol1","ext4", 3)
      
      val awsVol2 = Volume("myVol", Volume.AWSElasticBlockStore("vol2","ext4",readOnly = true))
      val myVolJson2 = Json.toJson(awsVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name mustEqual "myVol"
      readVol2.source match { 
        case Volume.AWSElasticBlockStore(volName,fsType,partition,readonly) => 
          volName mustEqual "vol2"
          fsType mustEqual "ext4"
          partition mustEqual 0
          readonly mustEqual true
        case _ => Failure("not an AWS EBS disk!")
      }   
      readVol2.source mustEqual Volume.AWSElasticBlockStore("vol2","ext4",0, true)
    } 
    "this can be done for the a serialised RDB source spec" >> {
      val monitors = List(
                    "10.16.154.78:6789",
                    "10.16.154.82:6789",
                    "10.16.154.83:6789")
      val rbd = Volume.RBD(monitors, "foo", "ext4", "kube", readOnly=true)            
      val rbdVol = Volume("rbdpd", rbd)
      val myVolJson = Json.toJson(rbdVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.source match { 
        case rbd: Volume.RBD => 
          rbd.monitors.length mustEqual 3
          rbd.fsType mustEqual "ext4"
          rbd.pool mustEqual "kube"
          rbd.image mustEqual "foo"
          rbd.user mustEqual "admin"
          rbd.keyring mustEqual "/etc/cepth/keyring"
          rbd.readOnly mustEqual true
        case _ => Failure("not an RBD volume!")
      }
      readVol.name mustEqual "rbdpd"
    }
    "a RDB volume spec can be deserialised straight from a JSON string" >> {
      val jsVal = Json.parse("""
        {
                "name": "rbdpd",
                "rbd": {
                    "monitors": [
                    "10.16.154.78:6789",
                    "10.16.154.82:6789",
                    "10.16.154.83:6789"
                    ],
                    "pool": "kube",
                    "image": "foo",
                    "user": "admin",
                    "secretRef": {
                        "name": "ceph-secret"
                     },
                    "fsType": "ext4"
                }
         }
      """)
      val res = Json.fromJson[Volume](jsVal)
      val ret: Result = res match {
          case JsSuccess(vol,path) =>
             vol.name mustEqual "rbdpd"
             vol.source match { 
              case rbd: Volume.RBD => 
                rbd.monitors.length mustEqual 3
                rbd.monitors.count { _.startsWith("10.16.154.") } mustEqual 3
                rbd.fsType mustEqual "ext4"
                rbd.readOnly mustEqual false
                rbd.secretRef mustEqual Some(LocalObjectReference("ceph-secret"))
              case _ => Failure("Unexpected source type for volume source: " + vol.source) 
             }
          case JsError(e) => Failure(e.toString)
      }   
      ret
    } 
    "an NFS volume spec can be written and read back in" >> {
      val nfsVol = Volume("myVol", Volume.NFS("myServer", "/usr/mypath"))
      val myVolJson = Json.toJson(nfsVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      val ret: Result =readVol.source match { 
        case Volume.NFS(server,path,readOnly) => 
          server mustEqual "myServer"
          path mustEqual "/usr/mypath"
          readOnly mustEqual false
        case _ => Failure("not a nfs volume!")
      }
      ret
    }
    "a Glusterfs volume spec can be written and read back in" >> {
      val gfsVol = Volume("myVol", Volume.Glusterfs("myEndpointsName", "/usr/mypath"))
      val myVolJson = Json.toJson(gfsVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      val ret: Result =readVol.source match { 
        case Volume.Glusterfs(endpoints,path,readOnly) => 
          endpoints mustEqual "myEndpointsName"
          path mustEqual "/usr/mypath"
          readOnly mustEqual false
        case _ => Failure("not a gfs volume")
      }
      ret
    }
    "an ISCSI volume spec can be written and read back in" >> {
      val iVol = Volume("myVol", Volume.ISCSI("127.0.0.1:3260", "iqn.2014-12.world.server:www.server.world"))
      val myVolJson = Json.toJson(iVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      val ret: Result =readVol.source match { 
        case is: Volume.ISCSI => 
          is.readOnly mustEqual false
          is.iqn mustEqual "iqn.2014-12.world.server:www.server.world"
          is.targetPortal mustEqual "127.0.0.1:3260"
        case _ => Failure("not a gfs volume")
      }
      ret
    }
    "a persistent claim spec can be written and read back in" >> {
      val pcVol = Volume("myVol", Volume.PersistentVolumeClaimRef("claim"))
      val myVolJson = Json.toJson(pcVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name mustEqual "myVol"
      val ret: Result =readVol.source match { 
        case Volume.PersistentVolumeClaimRef(claimName,readOnly) => 
          claimName mustEqual "claim"
          readOnly mustEqual false
        case _ => Failure("not a gfs volume")
      }
      ret
    }
  }
}
