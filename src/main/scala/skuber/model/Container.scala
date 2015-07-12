package skuber.model

import Model._
import java.util.Date

/**
 * @author David O'Riordan
 */
case class Container(
    name: String,
    image: Option[String] = None,
    command: Option[List[String]] = None,
    args: Option[List[String]] = None,
    workingDir: Option[String] = None,
    ports : Option[List[Container.Port]] = None,
    env: Option[List[EnvVar]] = None,
    resources: Option[Resource.Requirements] = None,
    volumeMounts: Option[List[Volume.Mount]] = None,
    livenessProbe: Option[Probe] = None,
    readinessProbe: Option[Probe] = None,
    lifeCycle: Option[Lifecycle] = None,
    terminationMessagePath: Option[String] = None,
    imagePullPolicy: Option[String] = None,
    securityContext: Option[Security.Context] = None)
    

object Container {
  case class Port(
      containerPort: Int,
      protocol: Option[String] = None,
      name: Option[String] = None,
      hostPort:Option[Int] = None,
      hostIP: Option[String] =None)
       
  sealed trait State { def id: String }
  case class Waiting(reason: Option[String]=None) extends State { def id="waiting" }
  case class Running(startingAt: Option[Date]) extends State { def id="running" }
  case class Terminated(
      exitCode: Int,
      signal: Option[Int] = None,
      reason: Option[String] = None,
      message: Option[String] = None,
      startedAt: Option[Date] = None,
      finishedAt: Option[Date] = None,
      containerID: Option[String] = None)
    extends State { def id="terminated" }
  
  case class Status(
      name: String,
      ready: Boolean,
      restartCount: Int,
      image: String,
      imageID: String,
      state: Option[State],
      lastState: Option[State]) 
      
}    