package skuber

import java.util.Date

/**
 * @author David O'Riordan
 */

case class Container(
    name: String,
    image: String,
    command: List[String] = List(),
    args: List[String] = List(),
    workingDir: Option[String] = None,
    ports : List[Container.Port] = List(),
    env: List[EnvVar] = List(),
    resources: Option[Resource.Requirements] = None,
    volumeMounts: List[Volume.Mount] = List(),
    livenessProbe: Option[Probe] = None,
    readinessProbe: Option[Probe] = None,
    lifeCycle: Option[Lifecycle] = None,
    terminationMessagePath: String = "/var/log/termination",
    imagePullPolicy: Container.PullPolicy.Value = Container.PullPolicy.IfNotPresent,
    securityContext: Option[Security.Context] = None)
      extends Limitable
    
object Container {
  
  object PullPolicy extends Enumeration {
    type PullPolicy = Value
    val Always, Never, IfNotPresent = Value
  }
  
  case class Port(
      containerPort: Int,
      protocol: Protocol.Value=Protocol.TCP,
      name: String = "",
      hostIP: String = "",
      hostPort:Option[Int] = None)
       
  sealed trait State { def id: String }
  case class Waiting(reason: Option[String] = None) extends State { def id="waiting" }
  case class Running(startedAt: Option[Timestamp]) extends State { def id="running" }
  case class Terminated(
      exitCode: Int,
      signal: Option[Int] = None,
      reason: Option[String] = None,
      message: Option[String] = None ,
      startedAt: Option[Timestamp] = None,
      finishedAt: Option[Timestamp] = None,
      containerID: Option[String] = None)
    extends State { def id="terminated" }
  
  case class Status(
      name: String,
      ready: Boolean,
      restartCount: Int,
      image: String,
      imageID: String,
      state: Option[State] = None,
      lastState: Option[State] = None,
      containerID: Option[String] = None) 
      
}    