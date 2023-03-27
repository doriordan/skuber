
// resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

val pekkoGroup = "org.apache.pekko"

val pekkoVersion = "0.0.0+26626-3e1231c3-SNAPSHOT"
val pekkoHttpVersion = "0.0.0+4332-87421a76-SNAPSHOT"

// the client API request/response handing uses Pekka Http
val pekkoHttp = pekkoGroup %% "pekko-http" % pekkoHttpVersion

// The `watch` and some other functionality uses Pekko streams
val pekkoStreams = pekkoGroup %% "pekko-stream" % pekkoVersion
val pekkoStreamsTestkit = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion % Test

// Skuber uses Pekko logging, so the examples config uses the Pekko slf4j logger with logback backend
val pekkoSlf4j = pekkoGroup %% "pekko-slf4j" % pekkoVersion
val logback = "ch.qos.logback" % "logback-classic" % "1.4.6" % Runtime

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.17.0"
val specs2 = "org.specs2" %% "specs2-core" % "4.19.2"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15"
val mockito = "org.mockito" % "mockito-core" % "4.6.1"
val scalaTestMockito = "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0"

val snakeYaml =  "org.yaml" % "snakeyaml" % "2.0"
val commonsIO = "commons-io" % "commons-io" % "2.11.0"
val commonsCodec = "commons-codec" % "commons-codec" % "1.15"

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.9.4"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

ThisBuild / version := "3.0.0-beta1"

sonatypeProfileName := "io.skuber"

ThisBuild / publishMavenStyle := true

ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / homepage := Some(url("https://github.com/doriordan"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/doriordan/skuber"),
    "scm:git@github.com:doriordan/skuber.git"
  )
)

ThisBuild / developers := List(Developer(id="doriordan", name="David ORiordan", email="doriordan@gmail.com", url=url("https://github.com/doriordan")))

lazy val commonSettings = Seq(
  organization := "io.skuber",
  crossScalaVersions := Seq("2.12.17", "2.13.10"),
  scalaVersion := "2.13.10",
  publishTo :=  sonatypePublishToBundle.value,
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  resolvers += "Apache Pekko Snapshots" at "https://repository.apache.org/content/groups/snapshots"
)

lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(
    pekkoHttp, pekkoStreams, playJson, snakeYaml, commonsIO, commonsCodec,
    scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test,
    pekkoStreamsTestkit, scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)

// This explicit Pekko dependency for the example build is not strictly necessary (the necessary dependencies will be transitively
// inherited from skuber dependency anyway) but clients might opt to explicitly specify such dependencies, for example to override version.
val pekkoActors = pekkoGroup %% "pekko-actor" % pekkoVersion

lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(pekkoActors, pekkoSlf4j, logback)
)

// by default run the deployment examples when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  assembly / mainClass := Some("skuber.examples.deployment.DeploymentExamples")
)

root / publishArtifact := false

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(skuber, examples)

lazy val skuber= (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies += scalaTest % "it",
  )

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)