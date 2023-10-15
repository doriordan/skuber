
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

// Core/common dependencies
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.17.0"
val specs2 = "org.specs2" %% "specs2-core" % "4.20.2"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.16"
val mockito = "org.mockito" % "mockito-core" % "4.11.0"
val scalaTestMockito = "org.scalatestplus" %% "mockito-4-11" % "3.2.16.0"
val snakeYaml =  "org.yaml" % "snakeyaml" % "2.2"
val commonsIO = "commons-io" % "commons-io" % "2.14.0"
val commonsCodec = "commons-codec" % "commons-codec" % "1.16.0"
val logback = "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime
val playJson = "com.typesafe.play" %% "play-json" % "2.10.1"
val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

ThisBuild / version := "3.0.0-beta3"

sonatypeProfileName := "io.skuber"

ThisBuild / publishMavenStyle  := true

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
  crossScalaVersions := Seq("2.13.12", "3.3.1"),
  scalaVersion := "3.3.1",
  publishTo :=  sonatypePublishToBundle.value,
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
)

// skuber core - contains the skuber model and core API - has no dependencies on Akka/Pekko
lazy val skuberSettings = Seq(
  libraryDependencies ++= Seq(
    playJson, snakeYaml, commonsIO, commonsCodec,
    scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test,
    scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)

lazy val core = (project in file("core"))
  .configs(IntegrationTest)
  .settings(
    name := "skuber-core",
    commonSettings,
    skuberSettings,
    libraryDependencies ++= Seq(typesafeConfig, scalaTest % "it")
  )

// Skuber Pekko client - concrete Kubernetes Scala client implementation based on Pekko HTTP and Pekko Streams
// Does not have any Akka dependencies

val pekkoGroup = "org.apache.pekko"

val pekkoVersion = "1.0.1"
val pekkoHttpVersion = "1.0.0"

val pekkoSlf4j = pekkoGroup %% "pekko-slf4j" % pekkoVersion
val pekkoHttp = pekkoGroup %% "pekko-http" % pekkoHttpVersion
val pekkoStream = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoStreamTestkit = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoActors = pekkoGroup %% "pekko-actor" % pekkoVersion

lazy val pekkoClientDependencies = Seq(pekkoActors, pekkoHttp, pekkoStream, pekkoSlf4j, logback, pekkoStreamTestkit,
                                       scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % "it, test")

lazy val pekko = (project in file("pekko"))
    .configs(IntegrationTest)
    .settings(name := "skuber-pekko")
    .settings(
      commonSettings,
      Defaults.itSettings,
      libraryDependencies ++= pekkoClientDependencies
    )
    .dependsOn(core)

// Skuber Akka BSL client - concrete Kubernetes Scala client implementation based on Akka HTTP and Akka Streams
// IMPORTANT - the versions of the Akka dependencies in this build of the Skuber Akka client are licensed using BSL
// (see https://www.lightbend.com/akka/license-faq) - please use the Skuber Pekko client unless you are certain you
// understand and accept the implications of the Akka BSL license.

val akkaBSLVersion = "2.8.5"
val akkaBSLHttpVersion = "10.5.2"

val akkaBSLSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaBSLVersion
val akkaBSLHttp = "com.typesafe.akka" %% "akka-http" % akkaBSLHttpVersion
val akkaBSLStream = "com.typesafe.akka" %% "akka-stream" % akkaBSLVersion
val akkaBSLStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaBSLVersion
val akkaBSLActors = "com.typesafe.akka" %% "akka-actor" % akkaBSLVersion

lazy val akkaBSLClientDependencies = Seq(akkaBSLActors, akkaBSLHttp, akkaBSLStream, akkaBSLSlf4j, logback, akkaBSLStreamTestKit,
  scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % "it, test")

lazy val akka = (project in file("akka"))
    .configs(IntegrationTest)
    .settings(
      name := "skuber-akka-bsl",
      commonSettings,
      Defaults.itSettings,
      libraryDependencies ++= akkaBSLClientDependencies
    )
    .dependsOn(core)

// Examples project
lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(pekkoActors, pekkoSlf4j, logback)
)

// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  assembly /mainClass := Some("skuber.examples.guestbook.Guestbook")
)

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(core)
  .dependsOn(pekko)

lazy val root = (project in file("."))
    .settings(
      publish / skip := true,
      commonSettings
    )
    .aggregate(core, akka, pekko, examples)

root / publishArtifact := false


