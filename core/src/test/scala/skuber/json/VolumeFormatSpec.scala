package skuber.json

import org.specs2.execute.{Failure, Result}
import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.model._
import PersistentVolumeClaim.VolumeMode
import skuber.json.format._
import skuber.model.LabelSelector.IsEqualRequirement

import scala.io.Source

/**
 * @author David O'Riordan
 */
class VolumeReadWriteSpec extends Specification {
  "This is a unit specification for the skuber json readers and writers for types that have multiple choices e.g. Volumes have multiple choices of Source type.\n ".txt 

  import skuber.model.Volume._

  "A PersistentVolumeClaim spec can be symmetrically written to json and the same value read back in \n" >> {
    "this can be done for the emptydir type source spec" >> {
      val pvc = PersistentVolumeClaim(
        metadata = ObjectMeta(
          name = "mypvc"
        ),
        spec = Some(PersistentVolumeClaim.Spec(
          accessModes = List(PersistentVolume.AccessMode.ReadWriteOnce),
          resources = Some(Resource.Requirements(limits=Map("storage" -> "30Gi"))),
          volumeName = Some("volume-name"),
          storageClassName = Some("a-storage-class-name"),
          volumeMode = Some(VolumeMode.Filesystem),
          selector = Some(LabelSelector(IsEqualRequirement("label","value")))
        ))
      )
      val pvcJson = Json.toJson(pvc)
      val readPvc = Json.fromJson[PersistentVolumeClaim](pvcJson).get
      readPvc.name must beEqualTo(pvc.name)
      readPvc.spec must beEqualTo(pvc.spec)
      readPvc.spec.get.storageClassName must beSome("a-storage-class-name")
    }

  }

  "A PersistentVolume with unsupported volume type can be read as json using GenericVolumeSource" >> {
    import skuber.model.Volume.GenericVolumeSource

    val podJsonSource = Source.fromURL(getClass.getResource("/exampleCephVolume.json"))
    val podJsonStr = podJsonSource.mkString

    val myPv = Json.parse(podJsonStr).as[PersistentVolume]
    myPv.spec.get.source match {
      case GenericVolumeSource(jsonStr) =>
        (Json.parse(jsonStr) \ "cephfs").isDefined must beTrue
      case _ =>
        ko("not a GenericVolumeSource!")
    }
  }

  // Volume reader and writer
  "A Volume spec can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for the emptydir type source spec" >> {
      val edVol = Volume("myVol", Volume.EmptyDir(
        Volume.HugePagesStorageMedium,
        sizeLimit = Some(Resource.Quantity("100M"))))
      val myVolJson = Json.toJson(edVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source must beEqualTo(Volume.EmptyDir(Volume.HugePagesStorageMedium, Some(Resource.Quantity("100M"))))

      // Ensure empty EmptyDir is still deserizeable
      val emptyEmptyDirJson = JsObject.empty
      val readEmptyDir = Json.fromJson[Volume.EmptyDir](emptyEmptyDirJson).get
      readEmptyDir.medium must beEqualTo(Volume.DefaultStorageMedium)
      readEmptyDir.sizeLimit must beEqualTo(None)
    }

    "this can be done for the a hostpath type source" >> {
      val hpVol = Volume("myVol", Volume.HostPath("myHostPath"))
      val myVolJson = Json.toJson(hpVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source match { 
        case Volume.HostPath(path,_) => path must beEqualTo("myHostPath")
        case _ => Failure("not a hostpath!")
      }
      readVol.source must beEqualTo(Volume.HostPath("myHostPath"))
    }

    "this can be done for the a secret type source spec" >> {
      val scVol = Volume("myVol", Volume.Secret("mySecretName"))
      val myVolJson = Json.toJson(scVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source match { 
        case Volume.Secret(secretName, _,_, _) => secretName must beEqualTo("mySecretName")
        case _ => Failure("not a secret!")
      }
      readVol.source must beEqualTo(Volume.Secret("mySecretName"))
    }

    "this can be done in a generic way for unsupported source specs" >> {
      val myVolStr =
        """{
          |  "name": "unsupported-volume",
          |  "some-fancy-new-volume-type": {
          |    "monitors": [
          |      "10.16.154.78:6789",
          |      "10.16.154.82:6789",
          |      "10.16.154.83:6789"
          |    ],
          |    "readOnly": true,
          |    "secretFile": "/etc/ceph/admin.secret",
          |    "user": "admin"
          |  }
          |}
        """.stripMargin
      val myVolJson = Json.parse(myVolStr)
      val genVol = Volume("unsupported-volume", Volume.GenericVolumeSource(myVolStr))
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("unsupported-volume")
      readVol.source match {
        case Volume.GenericVolumeSource(json) => Json.parse(json) must beEqualTo(myVolJson)
        case _ => Failure("not a generic volume!")
      }
      Json.toJson(genVol) must beEqualTo(myVolJson)
    }

    "this can be done for the a git repo source spec" >> {
      val gitVol = Volume("myVol", Volume.GitRepo("git://host/mygitrepo"))
      val myVolJson = Json.toJson(gitVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source match {
        case Volume.GitRepo(repoURL, revision, _) => repoURL must beEqualTo("git://host/mygitrepo")
        case _ => Failure("not a git repo!")
      }
      readVol.source must beEqualTo(Volume.GitRepo("git://host/mygitrepo", None))
      
      val gitVol2 = Volume("myVol2", Volume.GitRepo("git://host/mygitrepo2", Some("abdcef457677")))
      val myVolJson2 = Json.toJson(gitVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name must beEqualTo("myVol2")
      readVol2.source match {
        case Volume.GitRepo(repoURL, revision, _) =>
          repoURL must beEqualTo("git://host/mygitrepo2")
          revision must beSome("abdcef457677")
        case _ => Failure("not a git repo!")
      }
      readVol2.source must beEqualTo(Volume.GitRepo("git://host/mygitrepo2",Some("abdcef457677")))
    }

    "this can be done for the DownwardAPIVolumeSource spec" >> {
      val objectFieldSelector = ObjectFieldSelector("v1", "/mnt/api")
      val resourceFieldSelector = ResourceFieldSelector(Option("container"), Option(Resource.Quantity("1")), "resouce")
      val downwardApiVolumeFile1 = DownwardApiVolumeFile(objectFieldSelector, None, "/mnt", Option(resourceFieldSelector))
      val volume = Volume("myVol", DownwardApiVolumeSource(Option(644), List(downwardApiVolumeFile1)))

      val volumeJson = Json.toJson(volume)
      val readVolume = Json.fromJson[Volume](volumeJson).get

      readVolume must beEqualTo(volume)
    }

    "this can be done for the DownwardAPIVolumeSource spec with field selector apiVersion left at default" >> {
      val objectFieldSelector = ObjectFieldSelector(fieldPath = "/mnt/api")
      val resourceFieldSelector = ResourceFieldSelector(Option("container"), Option(Resource.Quantity("1")), "resouce")
      val downwardApiVolumeFile1 = DownwardApiVolumeFile(objectFieldSelector, None, "/mnt", Option(resourceFieldSelector))
      val volume = Volume("myVol", DownwardApiVolumeSource(Option(644), List(downwardApiVolumeFile1)))

      val volumeJson = Json.toJson(volume)
      val readVolume = Json.fromJson[Volume](volumeJson).get

      readVolume must beEqualTo(volume)
    }

    "this can be done for the a GCE Persistent Disk source spec" >> {
      val gceVol = Volume("myVol", Volume.GCEPersistentDisk("pd1","ext4",3))
      val myVolJson = Json.toJson(gceVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source must beEqualTo(Volume.GCEPersistentDisk("pd1","ext4", 3))
      
      val gceVol2 = Volume("myVol", Volume.GCEPersistentDisk("pd2","ext4",readOnly = true))
      val myVolJson2 = Json.toJson(gceVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name must beEqualTo("myVol")
      readVol2.source match { 
        case Volume.GCEPersistentDisk(pdName,fsType,partition,readonly) => 
          pdName must beEqualTo("pd2")
          fsType must beEqualTo("ext4")
          partition must beEqualTo(0)
          readonly must beEqualTo(true)
        case _ => Failure("not a GCE disk!")
      }   
      readVol2.source must beEqualTo(Volume.GCEPersistentDisk("pd2","ext4",0, true))
    }

    "this can be done for the a AWS EBS source spec" >> {
      val awsVol = Volume("myVol", Volume.AWSElasticBlockStore("vol1","ext4",3))
      val myVolJson = Json.toJson(awsVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      readVol.source match { 
        case Volume.AWSElasticBlockStore(volName,fsType,partition,readonly) => 
          volName must beEqualTo("vol1")
          fsType must beEqualTo("ext4")
          partition must beEqualTo(3)
          readonly must beEqualTo(false)
        case _ => Failure("not an AWS EBS volume!")
      }   
      readVol.source must beEqualTo(Volume.AWSElasticBlockStore("vol1","ext4", 3))
      
      val awsVol2 = Volume("myVol", Volume.AWSElasticBlockStore("vol2","ext4",readOnly = true))
      val myVolJson2 = Json.toJson(awsVol2)
      val readVol2 = Json.fromJson[Volume](myVolJson2).get
      readVol2.name must beEqualTo("myVol")
      readVol2.source match { 
        case Volume.AWSElasticBlockStore(volName,fsType,partition,readonly) => 
          volName must beEqualTo("vol2")
          fsType must beEqualTo("ext4")
          partition must beEqualTo(0)
          readonly must beEqualTo(true)
        case _ => Failure("not an AWS EBS disk!")
      }   
      readVol2.source must beEqualTo(Volume.AWSElasticBlockStore("vol2","ext4",0, true))
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
          rbd.monitors.length must beEqualTo(3)
          rbd.fsType must beEqualTo("ext4")
          rbd.pool must beEqualTo("kube")
          rbd.image must beEqualTo("foo")
          rbd.user must beEqualTo("admin")
          rbd.keyring must beEqualTo("/etc/cepth/keyring")
          rbd.readOnly must beEqualTo(true)
        case _ => Failure("not an RBD volume!")
      }
      readVol.name must beEqualTo("rbdpd")
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
             vol.name must beEqualTo("rbdpd")
             vol.source match { 
              case rbd: Volume.RBD => 
                rbd.monitors.length must beEqualTo(3)
                rbd.monitors.count { _.startsWith("10.16.154.") } must beEqualTo(3)
                rbd.fsType must beEqualTo("ext4")
                rbd.readOnly must beEqualTo(false)
                rbd.secretRef must beEqualTo(Some(LocalObjectReference("ceph-secret")))
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
      readVol.name must beEqualTo("myVol")
      val ret: Result =readVol.source match { 
        case Volume.NFS(server,path,readOnly) => 
          server must beEqualTo("myServer")
          path must beEqualTo("/usr/mypath")
          readOnly must beEqualTo(false)
        case _ => Failure("not a nfs volume!")
      }
      ret
    }

    "a Glusterfs volume spec can be written and read back in" >> {
      val gfsVol = Volume("myVol", Volume.Glusterfs("myEndpointsName", "/usr/mypath"))
      val myVolJson = Json.toJson(gfsVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      val ret: Result =readVol.source match { 
        case Volume.Glusterfs(endpoints,path,readOnly) => 
          endpoints must beEqualTo("myEndpointsName")
          path must beEqualTo("/usr/mypath")
          readOnly must beEqualTo(false)
        case _ => Failure("not a gfs volume")
      }
      ret
    }
    "an ISCSI volume spec can be written and read back in" >> {
      val iVol = Volume("myVol", Volume.ISCSI("127.0.0.1:3260", "iqn.2014-12.world.server:www.server.world"))
      val myVolJson = Json.toJson(iVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      val ret: Result =readVol.source match { 
        case is: Volume.ISCSI => 
          is.readOnly must beEqualTo(false)
          is.iqn must beEqualTo("iqn.2014-12.world.server:www.server.world")
          is.targetPortal must beEqualTo("127.0.0.1:3260")
        case _ => Failure("not a gfs volume")
      }
      ret
    }
    "a persistent claim spec can be written and read back in" >> {
      val pcVol = Volume("myVol", Volume.PersistentVolumeClaimRef("claim"))
      val myVolJson = Json.toJson(pcVol)
      val readVol = Json.fromJson[Volume](myVolJson).get
      readVol.name must beEqualTo("myVol")
      val ret: Result =readVol.source match { 
        case Volume.PersistentVolumeClaimRef(claimName,readOnly) => 
          claimName must beEqualTo("claim")
          readOnly must beEqualTo(false)
        case _ => Failure("not a gfs volume")
      }
      ret
    }
  }
}
