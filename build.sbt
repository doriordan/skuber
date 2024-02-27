import sbtassembly.AssemblyKeys.assembly
import sbtassembly.{MergeStrategy, PathList}
import sbtghactions.WorkflowStep.Use
import xerial.sbt.Sonatype.*
import sbtrelease.ReleasePlugin.autoImport.*
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*
import sbtrelease.{Version, versionFormatError}

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

val scala12Version = "2.12.13"
val scala13Version = "2.13.12"
val scala3Version = "3.3.1"

val currentScalaVersion = scala13Version

ThisBuild / scalaVersion := currentScalaVersion

val supportedScalaVersion = Seq(scala12Version, scala13Version, scala3Version)

val pekkoVersion = "1.0.2"

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.17.0"

val specs2 = "org.specs2" %% "specs2-core" % "4.20.5"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17"

val pekkoStreamTestKit = ("org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion).cross(CrossVersion.for3Use2_13)


val snakeYaml = "org.yaml" % "snakeyaml" % "2.0"

val commonsIO = "commons-io" % "commons-io" % "2.11.0"
val commonsCodec = "commons-codec" % "commons-codec" % "1.15"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk18on" % "1.76"


// the client API request/response handing uses Pekko Http
val pekkoHttp = ("org.apache.pekko" %% "pekko-http" % "1.0.1").cross(CrossVersion.for3Use2_13)
val pekkoStream = ("org.apache.pekko" %% "pekko-stream" % pekkoVersion).cross(CrossVersion.for3Use2_13)
val pekko = ("org.apache.pekko" %% "pekko-actor" % pekkoVersion).cross(CrossVersion.for3Use2_13)

// Skuber uses pekko logging, so the examples config uses the pekko slf4j logger with logback backend
val pekkoSlf4j = ("org.apache.pekko" %% "pekko-slf4j" % pekkoVersion).cross(CrossVersion.for3Use2_13)
val logback = "ch.qos.logback" % "logback-classic" % "1.4.6" % Runtime

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.10.0-RC7"
val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.5"

val awsJavaSdkCore = "com.amazonaws" % "aws-java-sdk-core" % "1.12.233"
val awsJavaSdkSts = "com.amazonaws" % "aws-java-sdk-sts" % "1.12.233"
val apacheCommonsLogging = "commons-logging" % "commons-logging" % "1.2"


Test / scalacOptions ++= Seq("-Yrangepos")

sonatypeProfileName := "io.github.hagay3"

ThisBuild / publishMavenStyle := true

ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / homepage := Some(url("https://github.com/hagay3"))

publishTo := sonatypePublishToBundle.value
sonatypeCredentialHost := Sonatype.sonatype01
ThisBuild / updateOptions := updateOptions.value.withGigahorse(false)

sonatypeProjectHosting := Some(GitHubHosting("hagay3", "skuber", "hagay3@gmail.com"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/hagay3/skuber"),
    "scm:git@github.com:hagay3/skuber.git"
  )
)

ThisBuild / developers := List(Developer(id = "hagay3", name = "Hagai Hillel", email = "hagay3@gmail.com", url = url("https://github.com/hagay3")))

lazy val commonSettings = Seq(
  organization := "io.github.hagay3",
  scalaVersion := currentScalaVersion,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  sonatypeCredentialHost := Sonatype.sonatype01,
  releaseNextVersion := {
    ver => Version(ver).map(_.bump(releaseVersionBump.value).string).getOrElse(versionFormatError(ver))
  },
  versionScheme := Some("early-semver"),
  releaseProcess := nextVersionSteps,
  asciiGraphWidth := 10000,
)

/** run the following command in order to generate github actions files:
  * sbt githubWorkflowGenerate && bash infra/ci/fix-workflows.sh
  */
def workflowJobMinikube(jobName: String, k8sServerVersion: String, excludedTestsTags: List[String] = List.empty): WorkflowJob = {

  val finalSbtCommand: String = {
    val additionalFlags: String = {
      if (excludedTestsTags.nonEmpty) {
        s"* -- ${excludedTestsTags.map(tag => s"-l $tag").mkString(" ")}"
      } else {
        ""
      }
    }

    "it:testOnly " + additionalFlags
  }

  WorkflowJob(
    scalas = List(scala13Version),
    id = jobName,
    name = jobName,
    steps = List(
      WorkflowStep.Checkout,
      WorkflowStep.Use(
        ref = UseRef.Public(owner = "manusa", repo = "actions-setup-minikube", ref = "v2.7.2"),
        params = Map(
          "minikube version" -> "v1.25.2",
          "kubernetes version" -> k8sServerVersion,
          "github token" -> "${{ secrets.GITHUB_TOKEN }}",
          "start args" -> "--extra-config=apiserver.disable-admission-plugins=ServiceAccount  --extra-config=apiserver.enable-admission-plugins=NamespaceLifecycle"),
        env = Map("SBT_OPTS" -> "-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=2G -Xmx8G -Xms6G")
      ),
      WorkflowStep.Sbt(List(finalSbtCommand))
    )
  )
}


inThisBuild(List(
  githubWorkflowJobSetup := List(Use(
    UseRef.Public("actions", "checkout", "v4"),
    name = Some("Checkout current branch (full)"),
    params = Map("fetch-depth" -> "0", "token" -> "${{ secrets.PERSONAL_GITHUB_TOKEN }}"))) :::
    WorkflowStep.SetupJava(githubWorkflowJavaVersions.value.toList) :::
    githubWorkflowGeneratedCacheSteps.value.toList,
  githubWorkflowJavaVersions += JavaSpec.temurin("17"),
  githubWorkflowBuildMatrixFailFast := Some(false),
  githubWorkflowScalaVersions := supportedScalaVersion,
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "It/compile"))),
  githubWorkflowAddedJobs := Seq(
    workflowJobMinikube(jobName = "integration-kubernetes-v1-19", k8sServerVersion = "v1.19.6", List("HorizontalPodAutoscalerV2Tag")),
    workflowJobMinikube(jobName = "integration-kubernetes-v1-20", k8sServerVersion = "v1.20.11", List("HorizontalPodAutoscalerV2Tag")),
    workflowJobMinikube(jobName = "integration-kubernetes-v1-21", k8sServerVersion = "v1.21.5", List("HorizontalPodAutoscalerV2Tag")),
    workflowJobMinikube(jobName = "integration-kubernetes-v1-22", k8sServerVersion = "v1.22.9", List("CustomResourceTag", "HorizontalPodAutoscalerV2Tag")),
    workflowJobMinikube(jobName = "integration-kubernetes-v1-23", k8sServerVersion = "v1.23.6", List("CustomResourceTag")),
    workflowJobMinikube(jobName = "integration-kubernetes-v1-24", k8sServerVersion = "v1.24.1", List("CustomResourceTag"))
  ),
  githubWorkflowPublish := Seq(
    WorkflowStep.Sbt(List("release with-defaults")),
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}")),
    WorkflowStep.Run(
      List("bash infra/ci/git-commit-and-push.sh")))))

lazy val nextVersionSteps = Seq[ReleaseStep](inquireVersions, setNextVersion)

lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(
    pekkoHttp, pekkoStream, playJson, snakeYaml, commonsIO, commonsCodec, bouncyCastle,
    awsJavaSdkCore, awsJavaSdkSts, apacheCommonsLogging, jacksonDatabind,
    scalaCheck % Test, specs2 % Test, pekkoStreamTestKit % Test,
    scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)

lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(pekko, pekkoSlf4j, logback, playJson)
)

// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  assembly / mainClass := Some("skuber.examples.guestbook.Guestbook")
)

lazy val `skuber-project` = (project in file("."))
  .settings(commonSettings,
    crossScalaVersions := Nil,
    publishArtifact := false)
  .aggregate(skuber, examples)

lazy val skuber = (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    crossScalaVersions := supportedScalaVersion,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies ++= Seq(scalaTest % "it", playJson)
  )


lazy val examples = (project in file("examples"))
  .settings(
    commonSettings,
    mergeStrategy,
    crossScalaVersions := supportedScalaVersion)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)

val mergeStrategy = Seq(
  assembly / assemblyMergeStrategy := {
    case PathList("module-info.class") => MergeStrategy.last
    case path if path.endsWith("/module-info.class") => MergeStrategy.last
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)