package skuber.json

import java.net.URL

import org.specs2.execute.{Failure, Result}
import org.specs2.mutable.Specification
import play.api.libs.json._
import skuber.Volume.{ConfigMapProjection, KeyToPath, SecretProjection, ServiceAccountTokenProjection}
import skuber._
import skuber.apps.StatefulSet
import skuber.json.format._

import scala.io.Source

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
      val readyProbe=Probe(action=HTTPGetAction(new URL("http://10.145.15.67:8100/ping")),
        timeoutSeconds = 10,
        initialDelaySeconds = 30,
        periodSeconds = Some(5),
        successThreshold = None,
        failureThreshold = Some(100))
      val startupProbe=Probe(action=HTTPGetAction(new URL("http://10.145.15.67:8100/ping")),
        periodSeconds = Some(10),
        failureThreshold = Some(30))
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
                               readinessProbe=Some(readyProbe),
                               startupProbe=Some(startupProbe),
                               lifecycle=Some(Lifecycle(preStop=Some(ExecAction(List("/bin/bash", "probe"))))),
                               terminationMessagePath=Some("/var/log/termination-message"),
                               terminationMessagePolicy=Some(Container.TerminationMessagePolicy.File),
                               securityContext=Some(SecurityContext(capabilities=Some(Security.Capabilities(add=List("CAP_KILL"),drop=List("CAP_AUDIT_WRITE")))))))
      val vols = List(Volume("myVol1", Volume.Glusterfs("myEndpointsName", "/usr/mypath")),
                      Volume("myVol2", Volume.ISCSI("127.0.0.1:3260", "iqn.2014-12.world.server:www.server.world")))
      val pdSpec=Spec(containers=cntrs,
                      volumes=vols,
                      dnsPolicy=DNSPolicy.ClusterFirst,
                      nodeSelector=Map("diskType" -> "ssd", "machineSize" -> "large"),
                      imagePullSecrets=List(LocalObjectReference("abc"),LocalObjectReference("def")),
                      securityContext=Some(PodSecurityContext(supplementalGroups=List(1, 2, 3))))
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
                "terminationMessagePolicy": "File",
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
                "terminationMessagePath": "/dev/termination-log"
              }
            ],
            "restartPolicy": "Always",
            "dnsPolicy": "Default",
            "tolerations": [{
              "key": "localhost.domain/url",
              "operator": "Exists"
            },
            {
              "key": "key",
              "operator": "Equal",
              "value": "value",
              "effect": "NoExecute"
            },
            {
              "effect": "NoSchedule",
              "operator": "Exists"
            }],
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
      myPod.spec.get.tolerations mustEqual List(ExistsToleration(Some("localhost.domain/url")),
        EqualToleration("key",Some("value"),Some(TolerationEffect.NoExecute)),
        ExistsToleration(None, Some(TolerationEffect.NoSchedule), None))

      val vols = myPod.spec.get.volumes
      vols.length mustEqual 2
      vols(0) mustEqual Volume("dns-token",Volume.Secret("token-system-dns"))
      
      val cntrs = myPod.spec.get.containers
      cntrs.length mustEqual 3
      cntrs(0).name mustEqual "etcd"
      cntrs(0).imagePullPolicy mustEqual Some(Container.PullPolicy.IfNotPresent)
      cntrs(0).terminationMessagePath mustEqual Some("/dev/termination-log")
      cntrs(0).terminationMessagePolicy mustEqual Some(Container.TerminationMessagePolicy.File)
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
      cntrs(2).imagePullPolicy equals None
      
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

    "a pod with nodeAffinity can be read and written as json" >> {
      import Affinity.{NodeAffinity, NodeSelectorOperator}
      import NodeAffinity.{PreferredSchedulingTerm, PreferredSchedulingTerms, RequiredDuringSchedulingIgnoredDuringExecution}

      val podJsonSource = s"""{ "apiVersion": "v1", "kind": "Pod", "metadata": { "name": "with-node-affinity" }, "spec": { "affinity": { "nodeAffinity": { "requiredDuringSchedulingIgnoredDuringExecution": { "nodeSelectorTerms": [ { "matchExpressions": [ { "key": "kubernetes.io/e2e-az-name", "operator": "In", "values": [ "e2e-az1", "e2e-az2" ] } ] } ] }, "preferredDuringSchedulingIgnoredDuringExecution": [ { "weight": 1, "preference": { "matchExpressions": [ { "key": "another-node-label-key", "operator": "In", "values": [ "another-node-label-value" ] } ] } } ] } }, "containers": [ { "name": "with-node-affinity", "image": "gcr.io/google_containers/pause:2.0" } ] } }"""


      val myPod = Json.parse(podJsonSource).as[Pod]
      myPod.spec.get.affinity must beSome(Affinity(nodeAffinity = Some(NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution = Some(RequiredDuringSchedulingIgnoredDuringExecution.requiredQuery("kubernetes.io/e2e-az-name", NodeSelectorOperator.In, List("e2e-az1", "e2e-az2"))),
          preferredDuringSchedulingIgnoredDuringExecution = PreferredSchedulingTerms(PreferredSchedulingTerm.preferredQuery(1, "another-node-label-key", NodeSelectorOperator.In, List("another-node-label-value")))))))
      val readPod = Json.fromJson[Pod](Json.toJson(myPod)).get
      myPod mustEqual readPod
    }

    "a pod with unsupported volume type can be read as json using GenericVolumeSource" >> {
      import skuber.Volume.GenericVolumeSource

      val podJsonSource = Source.fromURL(getClass.getResource("/examplePodWithCephVolume.json"))
      val podJsonStr = podJsonSource.mkString

      val myPod = Json.parse(podJsonStr).as[Pod]
      myPod.spec must beSome
      myPod.spec.get.volumes must haveSize(1)
      myPod.spec.get.volumes.head.source must beAnInstanceOf[GenericVolumeSource]
    }

    "NodeSelectorTerm be properly read and written as json" >> {
      import Affinity.{NodeSelectorOperator, NodeSelectorRequirement, NodeSelectorRequirements, NodeSelectorTerm}

      val nodeSelectorTermJsonSource = Source.fromURL(getClass.getResource("/exampleNodeSelectorTerm.json"))
      val nodeSelectorTermJson = nodeSelectorTermJsonSource.mkString
      val myTerm = Json.parse(nodeSelectorTermJson).as[NodeSelectorTerm]
      myTerm must_== NodeSelectorTerm(matchExpressions = NodeSelectorRequirements(NodeSelectorRequirement("kubernetes.io/e2e-az-name", NodeSelectorOperator.In, List("e2e-az1", "e2e-az2"))),
        matchFields = NodeSelectorRequirements(NodeSelectorRequirement("metadata.name", NodeSelectorOperator.In, List("some-node-name"))))
      val readTerm = Json.fromJson[NodeSelectorTerm](Json.toJson(myTerm)).get
      myTerm mustEqual readTerm
    }

    "NodeSelectorTerm with no matchExpressions be properly read and written as json" >> {
      import Affinity.{NodeSelectorOperator, NodeSelectorRequirement, NodeSelectorRequirements, NodeSelectorTerm}

      val nodeSelectorTermJsonSource = Source.fromURL(getClass.getResource("/exampleNodeSelectorTermNoMatchExpressions.json"))
      val nodeSelectorTermJson = nodeSelectorTermJsonSource.mkString
      val myTerm = Json.parse(nodeSelectorTermJson).as[NodeSelectorTerm]
      myTerm must_== NodeSelectorTerm(matchFields = NodeSelectorRequirements(NodeSelectorRequirement("metadata.name", NodeSelectorOperator.In, List("some-node-name"))))
      val readTerm = Json.fromJson[NodeSelectorTerm](Json.toJson(myTerm)).get
      myTerm mustEqual readTerm
    }

    "NodeSelectorTerm with no matchFields be properly read and written as json" >> {
      import Affinity.{NodeSelectorOperator, NodeSelectorRequirement, NodeSelectorRequirements, NodeSelectorTerm}

      val nodeSelectorTermJsonSource = Source.fromURL(getClass.getResource("/exampleNodeSelectorTermNoMatchFields.json"))
      val nodeSelectorTermJson = nodeSelectorTermJsonSource.mkString
      val myTerm = Json.parse(nodeSelectorTermJson).as[NodeSelectorTerm]
      myTerm must_== NodeSelectorTerm(matchExpressions = NodeSelectorRequirements(NodeSelectorRequirement("kubernetes.io/e2e-az-name", NodeSelectorOperator.In, List("e2e-az1", "e2e-az2"))))
      val readTerm = Json.fromJson[NodeSelectorTerm](Json.toJson(myTerm)).get
      myTerm mustEqual readTerm
    }

    "NodeSelectorTerm with empty be properly read and written as json" >> {
      import Affinity.NodeSelectorTerm

      val nodeSelectorTermJsonSource = Source.fromURL(getClass.getResource("/exampleNodeSelectorTermEmpty.json"))
      val nodeSelectorTermJson = nodeSelectorTermJsonSource.mkString
      val myTerm = Json.parse(nodeSelectorTermJson).as[NodeSelectorTerm]
      myTerm must_== NodeSelectorTerm()
      val readTerm = Json.fromJson[NodeSelectorTerm](Json.toJson(myTerm)).get
      myTerm mustEqual readTerm
    }

    "NodeAffinity be properly read and written as json" >> {
      import Affinity.{NodeAffinity, NodeSelectorOperator}
      import NodeAffinity.{PreferredSchedulingTerm, PreferredSchedulingTerms, RequiredDuringSchedulingIgnoredDuringExecution}

      val affinityJsonSource = s"""{ "nodeAffinity": { "requiredDuringSchedulingIgnoredDuringExecution": { "nodeSelectorTerms": [ { "matchExpressions": [ { "key": "kubernetes.io/e2e-az-name", "operator": "In", "values": [ "e2e-az1", "e2e-az2" ] } ] } ] }, "preferredDuringSchedulingIgnoredDuringExecution": [ { "weight": 1, "preference": { "matchExpressions": [ { "key": "another-node-label-key", "operator": "In", "values": [ "another-node-label-value" ] } ] } } ] } }""""

      val myAffinity = Json.parse(affinityJsonSource).as[Affinity]
      myAffinity must_== Affinity(nodeAffinity = Some(NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution = Some(RequiredDuringSchedulingIgnoredDuringExecution.requiredQuery("kubernetes.io/e2e-az-name", NodeSelectorOperator.In, List("e2e-az1", "e2e-az2"))),
          preferredDuringSchedulingIgnoredDuringExecution = PreferredSchedulingTerms(PreferredSchedulingTerm.preferredQuery(1, "another-node-label-key", NodeSelectorOperator.In, List("another-node-label-value"))))))
      val readAffinity = Json.fromJson[Affinity](Json.toJson(myAffinity)).get
      myAffinity mustEqual readAffinity
    }

    "NodeAffinity without preferences be properly read and written as json" >> {
      import Affinity.{NodeAffinity, NodeSelectorOperator}
      import NodeAffinity.{PreferredSchedulingTerms, RequiredDuringSchedulingIgnoredDuringExecution}

      val affinityJsonSource = s"""{ "nodeAffinity": { "requiredDuringSchedulingIgnoredDuringExecution": { "nodeSelectorTerms": [ { "matchExpressions": [ { "key": "kubernetes.io/e2e-az-name", "operator": "In", "values": [ "e2e-az1", "e2e-az2" ] } ] } ] } } }"""

      val myAffinity = Json.parse(affinityJsonSource).as[Affinity]
      myAffinity must_== Affinity(nodeAffinity = Some(NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution = Some(RequiredDuringSchedulingIgnoredDuringExecution.requiredQuery("kubernetes.io/e2e-az-name", NodeSelectorOperator.In, List("e2e-az1", "e2e-az2"))),
          preferredDuringSchedulingIgnoredDuringExecution = PreferredSchedulingTerms())))
      val readAffinity = Json.fromJson[Affinity](Json.toJson(myAffinity)).get
      myAffinity mustEqual readAffinity
    }

    "NodeAffinity without requirements be properly read and written as json" >> {
      import Affinity.{NodeAffinity, NodeSelectorOperator}
      import NodeAffinity.{PreferredSchedulingTerm, PreferredSchedulingTerms}

      val affinityJsonSource = Source.fromURL(getClass.getResource("/exampleAffinityNoRequirements.json"))
      val affinityJsonStr = affinityJsonSource.mkString

      val myAffinity = Json.parse(affinityJsonStr).as[Affinity]
      myAffinity must_== Affinity(nodeAffinity = Some(NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution = None,
          preferredDuringSchedulingIgnoredDuringExecution = PreferredSchedulingTerms(PreferredSchedulingTerm.preferredQuery(1, "another-node-label-key", NodeSelectorOperator.In, List("another-node-label-value"))))))
      val readAffinity = Json.fromJson[Affinity](Json.toJson(myAffinity)).get
      myAffinity mustEqual readAffinity
    }

    "PodAffinity can be properly read and written as json" >> {
      import Affinity.{NodeAffinity, NodeSelectorOperator}

      val affinityJsonSource = s"""{ "nodeAffinity": { "preferredDuringSchedulingIgnoredDuringExecution": [ { "weight": 1, "preference": { "matchExpressions": [ { "key": "another-node-label-key", "operator": "In", "values": [ "another-node-label-value" ] } ] } } ] } }"""
      

      val myAffinity = Json.parse(affinityJsonSource).as[Affinity]
      myAffinity must_== Affinity(nodeAffinity = Some(NodeAffinity(requiredDuringSchedulingIgnoredDuringExecution = None,
          preferredDuringSchedulingIgnoredDuringExecution = NodeAffinity.PreferredSchedulingTerms(NodeAffinity.PreferredSchedulingTerm.preferredQuery(1, "another-node-label-key", NodeSelectorOperator.In, List("another-node-label-value"))))))
      val readAffinity = Json.fromJson[Affinity](Json.toJson(myAffinity)).get
      myAffinity mustEqual readAffinity
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

    "a statefulset with pod affinity/anti-affinity can be read and written as json successfully" >> {
      val ssJsonSource=s"""{ "apiVersion": "apps/v1beta1", "kind": "StatefulSet", "metadata": { "name": "nginx-with-pod-affinity", "labels": { "app": "nginx", "security": "S1" } }, "spec": { "serviceName": "nginx", "replicas": 10, "selector": { "matchLabels": { "app": "nginx" } }, "template": { "metadata": { "labels": { "app": "nginx" } }, "spec": { "affinity": { "podAffinity": { "requiredDuringSchedulingIgnoredDuringExecution": [ { "labelSelector": { "matchExpressions": [{ "key": "security", "operator": "In", "values": [ "S1" ] }] }, "topologyKey": "failure-domain.beta.kubernetes.io/zone" } ] }, "podAntiAffinity": { "preferredDuringSchedulingIgnoredDuringExecution": [ { "weight": 100, "podAffinityTerm": { "labelSelector": { "matchExpressions": [{ "key": "security", "operator": "In", "values": [ "S2" ] }] }, "topologyKey": "kubernetes.io/hostname" } } ] } }, "containers": [ { "name": "nginx", "image": "nginx" } ] } } } }"""

      val ss = Json.parse(ssJsonSource).as[StatefulSet]

      val podAffinity = ss.spec.get.template.spec.get.affinity.get.podAffinity.get
      podAffinity.preferredDuringSchedulingIgnoredDuringExecution.size mustEqual (0)
      podAffinity.requiredDuringSchedulingIgnoredDuringExecution.size mustEqual (1)
      val affinityTerm=podAffinity.requiredDuringSchedulingIgnoredDuringExecution(0)
      val affinityTermRequirement=affinityTerm.labelSelector.get.requirements.head
      affinityTermRequirement match {
        case LabelSelector.InRequirement(_, values) => values mustEqual(List("S1"))
        case _ => failure("Parsed pod affinity term selector requirement should be of 'In' type")
      }
      affinityTermRequirement.key mustEqual "security"

      val podAntiAffinity = ss.spec.get.template.spec.get.affinity.get.podAntiAffinity.get
      podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution.size mustEqual (1)
      podAntiAffinity.requiredDuringSchedulingIgnoredDuringExecution.size mustEqual (0)
      val antiAffinityTerm=podAntiAffinity.preferredDuringSchedulingIgnoredDuringExecution(0).podAffinityTerm
      val antiAffinityTermRequirement=antiAffinityTerm.labelSelector.get.requirements.head
      antiAffinityTermRequirement match {
        case LabelSelector.InRequirement(_, values) => values mustEqual(List("S2"))
        case _ => failure("Parsed pod affinity term selector requirement should be of 'In' type")
      }
      antiAffinityTermRequirement.key mustEqual "security"

      // write and read it back in again and compare
      val json = Json.toJson(ss)
      val readSS = Json.fromJson[StatefulSet](json).get
      readSS mustEqual ss
    }
  }

  "a pod with a spec that sets many optional fields can be read and written as json successfully" >> {
    val podJsonSource=Source.fromURL(getClass.getResource("/examplePodExtendedSpec.json"))
    val podJsonStr = podJsonSource.mkString
    val pod = Json.parse(podJsonStr).as[Pod]

    val container=pod.spec.get.containers(0)
    container.securityContext.get.runAsUser mustEqual Some(1000)
    container.terminationMessagePolicy mustEqual Some(Container.TerminationMessagePolicy.FallbackToLogsOnError)
    container.terminationMessagePath mustEqual Some("/tmp/my-log")
    val mount=container.volumeMounts(0)
    mount.mountPropagation mustEqual Some(Volume.MountPropagationMode.HostToContainer)
    mount.readOnly mustEqual true
    mount.subPath mustEqual "subpath"

    pod.spec.get.securityContext.get.fsGroup mustEqual Some(2000)
    pod.spec.get.priority mustEqual Some(2)
    pod.spec.get.hostname mustEqual Some("abc")
    pod.spec.get.subdomain mustEqual Some("def")
    pod.spec.get.dnsPolicy mustEqual DNSPolicy.None
    pod.spec.get.hostNetwork mustEqual true
    pod.spec.get.shareProcessNamespace mustEqual Some(true)

    // write and read it back in again and compare
    val json = Json.toJson(pod)
    val readPod = Json.fromJson[Pod](json).get
    readPod mustEqual pod

    val configVolume = pod.spec.get.volumes.find(_.name == "config-volume").get
    configVolume.source must beAnInstanceOf[Volume.ProjectedVolumeSource]
    configVolume.source.asInstanceOf[Volume.ProjectedVolumeSource].defaultMode mustEqual Some(128)
    configVolume.source.asInstanceOf[Volume.ProjectedVolumeSource].sources mustEqual
      List(SecretProjection("verysecret",Some(List(KeyToPath("host-key-pub", "host_key.pub"), KeyToPath("other-key","worker_key"))),Some(false)),
      ConfigMapProjection("justConfig",Some(List(KeyToPath("host-key-pub", "host_key.pub"), KeyToPath("other-key", "worker_key"))),Some(false)),
      ServiceAccountTokenProjection(Some("vault"),Some(7200),"vault-token"))


  }

}
