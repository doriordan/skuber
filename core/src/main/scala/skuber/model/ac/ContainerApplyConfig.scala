package skuber.model.ac

import play.api.libs.json._
import play.api.libs.functional.syntax._
import skuber.model._
import skuber.json.format._

case class ContainerApplyConfig(
  name: Option[String] = None,
  image: Option[String] = None,
  command: Option[List[String]] = None,
  args: Option[List[String]] = None,
  workingDir: Option[String] = None,
  ports: Option[List[Container.Port]] = None,
  env: Option[List[EnvVar]] = None,
  resources: Option[Resource.Requirements] = None,
  volumeMounts: Option[List[Volume.Mount]] = None,
  livenessProbe: Option[Probe] = None,
  readinessProbe: Option[Probe] = None,
  lifecycle: Option[Lifecycle] = None,
  terminationMessagePath: Option[String] = None,
  terminationMessagePolicy: Option[Container.TerminationMessagePolicy.Value] = None,
  imagePullPolicy: Option[Container.PullPolicy.Value] = None,
  securityContext: Option[SecurityContext] = None,
  envFrom: Option[List[EnvFromSource]] = None,
  stdin: Option[Boolean] = None,
  stdinOnce: Option[Boolean] = None,
  tty: Option[Boolean] = None,
  volumeDevices: Option[List[Volume.Device]] = None,
  startupProbe: Option[Probe] = None
) {
  def exposePort(p: Container.Port): ContainerApplyConfig = copy(ports = Some(p :: ports.getOrElse(Nil)))
  def exposePort(port: Int): ContainerApplyConfig = exposePort(Container.Port(containerPort = port))

  def setEnvVar(n: String, v: String): ContainerApplyConfig = {
    val envVar = EnvVar(n, EnvVar.StringValue(v))
    copy(env = Some(env.getOrElse(Nil) :+ envVar))
  }

  def setEnvVarFromField(n: String, fieldPath: String): ContainerApplyConfig = {
    val envVar = EnvVar(n, EnvVar.FieldRef(fieldPath))
    copy(env = Some(env.getOrElse(Nil) :+ envVar))
  }

  def withWorkingDir(wd: String): ContainerApplyConfig = copy(workingDir = Some(wd))
  def withArgs(arg: String*): ContainerApplyConfig = copy(args = Some(arg.toList))
  def withEntrypoint(cmd: String*): ContainerApplyConfig = copy(command = Some(cmd.toList))

  def withTerminationMessagePath(path: String): ContainerApplyConfig = copy(terminationMessagePath = Some(path))
  def withTerminationMessagePolicy(policy: Container.TerminationMessagePolicy.Value): ContainerApplyConfig =
    copy(terminationMessagePolicy = Some(policy))

  def limitCPU(cpu: Resource.Quantity): ContainerApplyConfig = addResourceLimit(Resource.cpu, cpu)
  def limitMemory(mem: Resource.Quantity): ContainerApplyConfig = addResourceLimit(Resource.memory, mem)
  def addResourceLimit(name: String, limit: Resource.Quantity): ContainerApplyConfig = {
    val currResources = resources.getOrElse(Resource.Requirements())
    val newLimits = currResources.limits + (name -> limit)
    copy(resources = Some(Resource.Requirements(newLimits, currResources.requests)))
  }

  def requestCPU(cpu: Resource.Quantity): ContainerApplyConfig = addResourceRequest(Resource.cpu, cpu)
  def requestMemory(mem: Resource.Quantity): ContainerApplyConfig = addResourceRequest(Resource.memory, mem)
  def addResourceRequest(name: String, req: Resource.Quantity): ContainerApplyConfig = {
    val currResources = resources.getOrElse(Resource.Requirements())
    val newReqs = currResources.requests + (name -> req)
    copy(resources = Some(Resource.Requirements(currResources.limits, newReqs)))
  }

  def mount(name: String, path: String, readOnly: Boolean = false): ContainerApplyConfig =
    copy(volumeMounts = Some(Volume.Mount(name, path, readOnly) :: volumeMounts.getOrElse(Nil)))

  def withImagePullPolicy(policy: Container.PullPolicy.Value): ContainerApplyConfig =
    copy(imagePullPolicy = Some(policy))

  def withLivenessProbe(probe: Probe): ContainerApplyConfig = copy(livenessProbe = Some(probe))
  def withReadinessProbe(probe: Probe): ContainerApplyConfig = copy(readinessProbe = Some(probe))
  def withStartupProbe(probe: Probe): ContainerApplyConfig = copy(startupProbe = Some(probe))
  def withSecurityContext(sc: SecurityContext): ContainerApplyConfig = copy(securityContext = Some(sc))
}

object ContainerApplyConfig {

  def apply(name: String, image: String): ContainerApplyConfig =
    ContainerApplyConfig(name = Some(name), image = Some(image))

  implicit val writes: OWrites[ContainerApplyConfig] = {
    val partOne = (
      (JsPath \ "name").writeNullable[String] and
      (JsPath \ "image").writeNullable[String] and
      (JsPath \ "command").writeNullable[List[String]] and
      (JsPath \ "args").writeNullable[List[String]] and
      (JsPath \ "workingDir").writeNullable[String] and
      (JsPath \ "ports").writeNullable[List[Container.Port]] and
      (JsPath \ "env").writeNullable[List[EnvVar]] and
      (JsPath \ "resources").writeNullable[Resource.Requirements] and
      (JsPath \ "volumeMounts").writeNullable[List[Volume.Mount]] and
      (JsPath \ "livenessProbe").writeNullable[Probe] and
      (JsPath \ "readinessProbe").writeNullable[Probe]
    ).tupled

    val partTwo = (
      (JsPath \ "lifecycle").writeNullable[Lifecycle] and
      (JsPath \ "terminationMessagePath").writeNullable[String] and
      (JsPath \ "terminationMessagePolicy").writeNullable[String] and
      (JsPath \ "imagePullPolicy").writeNullable[String] and
      (JsPath \ "securityContext").writeNullable[SecurityContext] and
      (JsPath \ "envFrom").writeNullable[List[EnvFromSource]] and
      (JsPath \ "stdin").writeNullable[Boolean] and
      (JsPath \ "stdinOnce").writeNullable[Boolean] and
      (JsPath \ "tty").writeNullable[Boolean] and
      (JsPath \ "volumeDevices").writeNullable[List[Volume.Device]] and
      (JsPath \ "startupProbe").writeNullable[Probe]
    ).tupled

    OWrites[ContainerApplyConfig] { c =>
      val p1 = partOne.writes((c.name, c.image, c.command, c.args, c.workingDir, c.ports, c.env, c.resources, c.volumeMounts, c.livenessProbe, c.readinessProbe))
      val p2 = partTwo.writes((c.lifecycle, c.terminationMessagePath, c.terminationMessagePolicy.map(_.toString), c.imagePullPolicy.map(_.toString), c.securityContext, c.envFrom, c.stdin, c.stdinOnce, c.tty, c.volumeDevices, c.startupProbe))
      p1.deepMerge(p2)
    }
  }
}
