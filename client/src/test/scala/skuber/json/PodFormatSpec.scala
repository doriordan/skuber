package skuber.json

import org.specs2.mutable.Specification // for unit-style testing
import org.specs2.execute.Result
import org.specs2.execute.Failure
import org.specs2.execute.Success

import scala.math.BigInt
import scala.io.Source

import java.util.Calendar
import java.net.URL

import skuber._
import format._;

import play.api.libs.json._



/**
 * @author David O'Riordan
 */
class PodFormatSpec extends Specification {
  "This is a unit specification for the skuber Pod related json formatter.\n ".txt
  
import Pod._
  
  // Pod reader and writer
  "A Pod can be symmetrically written to json and the same value read back in\n" >> {
    "this can be done for a simple Pod with just a name" >> {
      val myPod = Pod.named("myPod")
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get
      myPod mustEqual readPod    
    }
    "this can be done for a simple Pod with just a name and namespace set" >> {
      val myPod = Namespace("myNamespace").pod("myPod")
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get
      myPod mustEqual readPod    
    } 
    "this can be done for a Pod with a simple, single container spec" >> {
      val myPod = Namespace("myNamespace").
                    pod("myPod",Spec(Container("myContainer", "myImage")::Nil))
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get
      myPod mustEqual readPod
    }
    "this can be done for a Pod with a more complex spec" >> {
      val cntrs=List(Container("myContainer", "myImage"),
                     Container(name="myContainer2", 
                               image = "myImage2", 
                               command=List("bash","ls"),
                               workingDir=Some("/home/skuber"),
                               ports=List(Container.Port(3234), Container.Port(3256,name="svc", hostIP="10.101.35.56")),
                               env=List(EnvVar("HOME", "/home/skuber")),
                               resources=Some(Resource.Requirements(limits=Map("cpu" -> "0.1"))),  
                               volumeMounts=List(Volume.Mount("mnt1","/mt1"), 
                                                 Volume.Mount("mnt2","/mt2", readOnly = true)),
                               readinessProbe=Some(Probe(HTTPGetAction(new URL("http://10.145.15.67:8100/ping")))),
                               lifecycle=Some(Lifecycle(preStop=Some(ExecAction(List("/bin/bash", "probe"))))),
                               securityContext=Some(Security.Context(capabilities=Some(Security.Capabilities(add=List("CAP_KILL"),drop=List("CAP_AUDIT_WRITE")))))
                              )
                     )
      val vols = List(Volume("myVol1", Volume.Glusterfs("myEndpointsName", "/usr/mypath")),
                      Volume("myVol2", Volume.ISCSI("127.0.0.1:3260", "iqn.2014-12.world.server:www.server.world")))
      val pdSpec=Spec(containers=cntrs,
                      volumes=vols,
                      dnsPolicy=DNSPolicy.ClusterFirst,
                      nodeSelector=Map("diskType" -> "ssd", "machineSize" -> "large"),
                      imagePullSecrets=List(LocalObjectReference("abc"),LocalObjectReference("def"))
                     )
      val myPod = Namespace("myNamespace").pod("myPod",pdSpec)
                            
      val writtenPod = Json.toJson(myPod)
      val strs=Json.stringify(writtenPod)
      val readPodJsResult = Json.fromJson[Pod](writtenPod)
     
      val ret: Result = readPodJsResult match {
        case JsError(e) => Failure(e.toString)    
        case JsSuccess(readPod,_) => 
          readPod mustEqual myPod
      }   
      ret
    }
    "a quite complex pod can be read from json" >> {
      val podJsonStr="""
        {
          "kind": "Pod",
          "apiVersion": "v1",
          "metadata": {
            "name": "kube-dns-v3-i5fzg",
            "generateName": "kube-dns-v3-",
            "namespace": "default",
            "selfLink": "/api/v1/namespaces/default/pods/kube-dns-v3-i5fzg",
            "uid": "66a5d354-42a0-11e5-9586-0800279dd272",
            "resourceVersion": "31066",
            "creationTimestamp": "2015-08-14T16:20:38Z",
            "labels": {
              "k8s-app": "kube-dns",
              "kubernetes.io/cluster-service": "true",
              "version": "v3"
            },
            "annotations": {
              "kubernetes.io/created-by": "{\"kind\":\"SerializedReference\",\"apiVersion\":\"v1\",\"reference\":{\"kind\":\"ReplicationController\",\"namespace\":\"default\",\"name\":\"kube-dns-v3\",\"uid\":\"8267387c-4239-11e5-9586-0800279dd272\",\"apiVersion\":\"v1\",\"resourceVersion\":\"27\"}}"
            }
          },
          "spec": {
            "volumes": [
              {
                "name": "dns-token",
                "secret": {
                  "secretName": "token-system-dns"
                }
              },
              {
                "name": "default-token-zmwgp",
                "secret": {
                  "secretName": "default-token-zmwgp"
                }
              }
            ],
            "containers": [
              {
                "name": "etcd",
                "image": "gcr.io/google_containers/etcd:2.0.9",
                "command": [
                  "/usr/local/bin/etcd",
                  "-listen-client-urls",
                  "http://127.0.0.1:2379,http://127.0.0.1:4001",
                  "-advertise-client-urls",
                  "http://127.0.0.1:2379,http://127.0.0.1:4001",
                  "-initial-cluster-token",
                  "skydns-etcd"
                ],
                "resources": {
                  "limits": {
                    "cpu": "100m"
                  }
                },
                "volumeMounts": [
                  {
                    "name": "default-token-zmwgp",
                    "readOnly": true,
                    "mountPath": "/var/run/secrets/kubernetes.io/serviceaccount"
                  }
                ],
                "terminationMessagePath": "/dev/termination-log",
                "imagePullPolicy": "IfNotPresent"
              },
              {
                "name": "kube2sky",
                "image": "gcr.io/google_containers/kube2sky:1.9",
                "args": [
                  "-domain=cluster.local",
                  "-kubecfg_file=/etc/dns_token/kubeconfig"
                ],
                "resources": {
                  "limits": {
                    "cpu": "100m"
                  }
                },
                "volumeMounts": [
                  {
                    "name": "dns-token",
                    "readOnly": true,
                    "mountPath": "/etc/dns_token"
                  },
                  {
                    "name": "default-token-zmwgp",
                    "readOnly": true,
                    "mountPath": "/var/run/secrets/kubernetes.io/serviceaccount"
                  }
                ],
                "terminationMessagePath": "/dev/termination-log",
                "imagePullPolicy": "IfNotPresent"
              },
              {
                "name": "skydns",
                "image": "gcr.io/google_containers/skydns:2015-03-11-001",
                "args": [
                  "-machines=http://localhost:4001",
                  "-addr=0.0.0.0:53",
                  "-domain=cluster.local."
                ],
                "ports": [
                  {
                    "name": "dns",
                    "containerPort": 53,
                    "protocol": "UDP"
                  },
                  {
                    "name": "dns-tcp",
                    "containerPort": 53,
                    "protocol": "TCP"
                  }
                ],
                "resources": {
                  "limits": {
                    "cpu": "100m"
                  }
                },
                "volumeMounts": [
                  {
                    "name": "default-token-zmwgp",
                    "readOnly": true,
                    "mountPath": "/var/run/secrets/kubernetes.io/serviceaccount"
                  }
                ],
                "livenessProbe": {
                  "exec": {
                    "command": [
                      "/bin/sh",
                      "-c",
                      "nslookup kubernetes.default.cluster.local localhost \u003e/dev/null"
                    ]
                  },
                  "initialDelaySeconds": 30,
                  "timeoutSeconds": 5
                },
                "terminationMessagePath": "/dev/termination-log",
                "imagePullPolicy": "IfNotPresent"
              }
            ],
            "restartPolicy": "Always",
            "dnsPolicy": "Default",
            "serviceAccount": "default",
            "nodeName": "10.245.1.5"
          },
          "status": {
            "phase": "Running",
            "conditions": [
              {
                "type": "Ready",
                "status": "False"
              }
            ],
            "hostIP": "10.245.1.5",
            "podIP": "10.246.3.2",
            "startTime": "2015-08-14T16:22:09Z",
            "containerStatuses": [
              {
                "name": "skydns",
                "state": {
                  "running": {
                    "startedAt": "2015-08-15T08:06:50Z"
                  }
                },
                "lastState": {
                  "terminated": {
                    "exitCode": 2,
                    "startedAt": "2015-08-15T08:06:10Z",
                    "finishedAt": "2015-08-15T08:06:49Z",
                    "containerID": "docker://ec96c0a87e374d1b2f309c102b13e88a2605a6df0017472a6d7f808b559324aa"
                  }
                },
                "ready": false,
                "restartCount": 3,
                "image": "gcr.io/google_containers/skydns:2015-03-11-001",
                "imageID": "docker://791ddf327076e0fd35a1125568a56c05ee1f1dfd7a165c74f4d489d8a5e65ac5",
                "containerID": "docker://b749514138c319522e958204f0d8e0dd0cc87a91b90779a48559fab4fd5886a1"
              },
              {
                "name": "etcd",
                "state": {
                  "running": {
                    "startedAt": "2015-08-14T16:22:09Z"
                  }
                },
                "lastState": {},
                "ready": true,
                "restartCount": 0,
                "image": "gcr.io/google_containers/etcd:2.0.9",
                "imageID": "docker://b6b9a86dc06aa1361357ca1b105feba961f6a4145adca6c54e142c0be0fe87b0",
                "containerID": "docker://d01f4c93114da2ac4e930c709bcfbe0a641ec67cdf1190849a7d3237ddb76202"
              },
              {
                "name": "kube2sky",
                "state": {
                  "running": {
                    "startedAt": "2015-08-14T16:21:22Z"
                  }
                },
                "lastState": {},
                "ready": true,
                "restartCount": 0,
                "image": "gcr.io/google_containers/kube2sky:1.9",
                "imageID": "docker://dbcdf588b1ed09a481f7ba0ec2ff15f552035a8b3a18afa2fa70d45164581d97",
                "containerID": "docker://f8777419ce544752db9da3225529400ee18ae1608835bfa474e22649c9bb2cef"
              }
            ]
          }
      }
      """
      val myPod = Json.parse(podJsonStr).as[Pod]
      myPod.kind mustEqual "Pod"
      myPod.name mustEqual "kube-dns-v3-i5fzg"
      myPod.metadata.labels("k8s-app") mustEqual "kube-dns"
      
      myPod.spec.get.dnsPolicy mustEqual DNSPolicy.Default
      myPod.spec.get.restartPolicy mustEqual RestartPolicy.Always
      
      val vols = myPod.spec.get.volumes
      vols.length mustEqual 2
      vols(0) mustEqual Volume("dns-token",Volume.Secret("token-system-dns"))
      
      val cntrs = myPod.spec.get.containers
      cntrs.length mustEqual 3
      cntrs(0).name mustEqual "etcd"
      cntrs(0).imagePullPolicy mustEqual Container.PullPolicy.IfNotPresent
      cntrs(0).resources.get.limits("cpu") mustEqual Resource.Quantity("100m")
      cntrs(0).command.length  mustEqual 7
      
      val etcdVolMounts=cntrs(0).volumeMounts
      etcdVolMounts.length mustEqual 1
      etcdVolMounts(0).name mustEqual "default-token-zmwgp"
     
      val probe = cntrs(2).livenessProbe.get 
      probe.action match {
        case ExecAction(command) => command.length mustEqual 3
        case _ => failure("liveness probe action must be an ExecAction")
      }
      probe.initialDelaySeconds mustEqual 30
      probe.timeoutSeconds mustEqual 5
      
      val ports = cntrs(2).ports // skyDNS ports
      ports.length mustEqual 2
      val udpDnsPort = ports(0)
      udpDnsPort.containerPort mustEqual 53
      udpDnsPort.protocol mustEqual Protocol.UDP
      udpDnsPort.name mustEqual "dns"
      
      val tcpDnsPort = ports(1)
      tcpDnsPort.containerPort mustEqual 53
      tcpDnsPort.protocol mustEqual Protocol.TCP
      tcpDnsPort.name mustEqual "dns-tcp"
      
      cntrs(2).image equals "gcr.io/google_containers/skydns:2015-03-11-001"
      
      val status = myPod.status.get
      status.conditions(0) mustEqual Pod.Condition("Ready","False")
      status.phase.get mustEqual Pod.Phase.Running
      val cntrStatuses = status.containerStatuses
      cntrStatuses.length mustEqual 3
      cntrStatuses(0).restartCount mustEqual 3
      cntrStatuses(0).lastState.get match {
        case c: Container.Terminated => 
          c.exitCode mustEqual 2 
          c.containerID.get mustEqual "docker://ec96c0a87e374d1b2f309c102b13e88a2605a6df0017472a6d7f808b559324aa"
        case _ => failure("container must be terminated")
      }
      cntrStatuses(2).state.get match {
        case Container.Running(startTime) if (startTime.nonEmpty) => 
          startTime.get.getHour mustEqual 16 // just a spot check
      }
      // write and read back in again, compare
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get 
      myPod mustEqual readPod
    }
    
    "a complex podlist can be read and written as json" >> {
      val podListJsonSource = Source.fromURL(getClass.getResource("/examplePodList.json"))
      val podListJsonStr = podListJsonSource.mkString
 
      val myPods = Json.parse(podListJsonStr).as[PodList]
      myPods.kind mustEqual "PodList"
      myPods.metadata.get.resourceVersion mustEqual "977"
      myPods.items.length mustEqual 22
      myPods.items(21).status.get.containerStatuses.exists( cs => cs.name.equals("grafana")) mustEqual true
       // write and read back in again, compare
      val readPods = Json.fromJson[PodList](Json.toJson(myPods)).get 
      myPods mustEqual readPods
    }
  }    
}