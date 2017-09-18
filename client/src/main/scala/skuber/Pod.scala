package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */
case class Pod(
  	val kind: String ="Pod",
  	override val apiVersion: String = v1,
    val metadata: ObjectMeta,
    spec: Option[Pod.Spec] = None,
    status: Option[Pod.Status] = None) 
      extends ObjectResource with Limitable

object Pod {

  val specification = CoreResourceSpecification(
      scope = ResourceSpecification.Scope.Namespaced,
      names = ResourceSpecification.Names(plural="pods",singular="pod",kind="Pod",shortNames=List("po"))
  )
  implicit val poDef = new ResourceDefinition[Pod] { def spec = specification }
  implicit val poListDef = new ResourceDefinition[PodList] { def spec = specification }

  def named(name: String) = Pod(metadata=ObjectMeta(name=name))
  def apply(name: String, spec: Pod.Spec) : Pod = Pod(metadata=ObjectMeta(name=name), spec = Some(spec))
  
  import DNSPolicy._
  case class Spec(
    containers: List[Container] = List(), // should have at least one member
    volumes: List[Volume] = Nil,
    restartPolicy: RestartPolicy.RestartPolicy = RestartPolicy.Always,
    terminationGracePeriodSeconds: Option[Int] = None,
    activeDeadlineSeconds: Option[Int] = None,
    dnsPolicy: DNSPolicy.DNSPolicy = Default,
    nodeSelector: Map[String, String] = Map(),
    serviceAccountName: String ="",
    nodeName: String = "",
    hostNetwork: Boolean = false,
    imagePullSecrets: List[LocalObjectReference] = List()) {
     
    // a few convenience methods for fluently building out a pod spec
    def addContainer(c: Container) = { this.copy(containers = c :: containers) }
    def addVolume(v: Volume) = { this.copy(volumes = v :: volumes) }
    def addNodeSelector(kv: Tuple2[String,String]) = {
      this.copy(nodeSelector = this.nodeSelector + kv)
    }
    def addImagePullSecretRef(ref: String) = {
      val loref = LocalObjectReference(ref)
      this.copy(imagePullSecrets = loref :: this.imagePullSecrets)
    }
    def withTerminationGracePeriodSeconds(gp: Int) = this.copy(terminationGracePeriodSeconds = Some(gp))
    def withActiveDeadlineSeconds(ad: Int) = this.copy(activeDeadlineSeconds = Some(ad))
    def withDnsPolicy(dp: DNSPolicy.DNSPolicy) = this.copy(dnsPolicy=dp)
    def withNodeName(nn: String) = this.copy(nodeName = nn)
    def withServiceAccountName(san: String) = this.copy(serviceAccountName = san)
    def withRestartPolicy(rp: RestartPolicy.RestartPolicy) = this.copy(restartPolicy = rp)
    def useHostNetwork = this.copy(hostNetwork=true)
   }
   
  object Phase extends Enumeration {
    type Phase = Value
    val Pending, Running, Succeeded, Failed, Unknown = Value
  }
           
  case class Status(
    phase: Option[Phase.Phase] = None,
    conditions: List[Condition] = Nil,
    message: Option[String] = None,
    reason: Option[String] = None,
    hostIP: Option[String] = None,
    podIP: Option[String] = None,
    startTime: Option[Timestamp] = None,
    containerStatuses: List[Container.Status] = Nil)

  case class Condition(_type : String="Ready", status: String)
 
  case class Template(
    val kind: String ="PodTemplate",
    override val apiVersion: String = v1,
    val metadata: ObjectMeta = ObjectMeta(),
    spec: Option[Template.Spec] = None) 
    extends ObjectResource
  {  
    def withResourceVersion(version: String) = this.copy(metadata = metadata.copy(resourceVersion=version))

    def addLabel(label: Tuple2[String, String]) : Template = this.copy(metadata = metadata.copy(labels = metadata.labels + label))
    def addLabel(label: String) : Template = addLabel(label -> "") 
    def addLabels(newLabels: Map[String, String]) = this.copy(metadata=metadata.copy(labels = metadata.labels ++  newLabels))
    def addAnnotation(anno: Tuple2[String, String]) : Template = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))
    def addAnnotation(anno: String) : Template = addAnnotation(anno -> "") 
    def addAnnotations(newAnnos: Map[String, String]) = this.copy(metadata=metadata.copy(annotations = metadata.annotations ++ newAnnos))
    def withTemplateSpec(spec: Template.Spec) = this.copy(spec=Some(spec))
    def withPodSpec(podSpec: Pod.Spec) = this.copy(spec=Some(Pod.Template.Spec(spec=Some(podSpec))))
  }
    
  object Template {

    val specification = CoreResourceSpecification(
      scope = ResourceSpecification.Scope.Namespaced,
      names = ResourceSpecification.Names(
        plural="podtemplates",
        singular="podtemplate",
        kind="PodTemplate",
        shortNames=Nil
      )
    )
    implicit val ptDef = new ResourceDefinition[Pod.Template] { def spec=specification }
    implicit val ptListDef = new ResourceDefinition[PodTemplateList] { def spec=specification }

    def named(name: String) : Pod.Template = Pod.Template(metadata=ObjectMeta(name=name))
     case class Spec(
         metadata: ObjectMeta = ObjectMeta(),
         spec: Option[Pod.Spec] = None) {
       
       def addLabel(label: Tuple2[String, String]) : Spec = this.copy(metadata = metadata.copy(labels = metadata.labels + label))
       def addLabels(newLabels: Map[String, String]) = this.copy(metadata=metadata.copy(labels = metadata.labels ++  newLabels))
       def addAnnotation(anno: Tuple2[String, String]) : Spec = this.copy(metadata = metadata.copy(annotations = metadata.annotations + anno))
       def addAnnotations(newAnnos: Map[String, String]) = this.copy(metadata=metadata.copy(annotations = metadata.annotations ++ newAnnos))
       def addContainer(container: Container) : Spec = {
         val newPodSpec = this.spec.getOrElse(Pod.Spec(Nil)).addContainer(container)
         this.copy(spec=Some(newPodSpec))
       }
       def withPodSpec(spec: Pod.Spec): Spec = this.copy(spec=Some(spec))
     }       
     object Spec {
       def named(name: String) : Spec = Spec(metadata=ObjectMeta(name=name))
     }
   } 
}