
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

// Core/common dependencies
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.19.0"
val specs2 = "org.specs2" %% "specs2-core" % "4.23.0"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
val mockito = "org.mockito" % "mockito-core" % "5.20.0"
val scalaTestMockito = "org.scalatestplus" %% "mockito-5-18" % "3.2.19.0"
val snakeYaml =  "org.yaml" % "snakeyaml" % "2.5"
val commonsCodec = "commons-codec" % "commons-codec" % "1.20.0"
val commonsIO = "commons-io" % "commons-io" % "2.21.0"
val typesafeConfig = "com.typesafe" % "config" % "1.4.5"
val logback = "ch.qos.logback" % "logback-classic" % "1.4.14" % Runtime
val playJson = "org.playframework" %% "play-json" % "3.0.6"

scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

ThisBuild / version := "3.0.0"

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

Global / concurrentRestrictions := Seq(
  Tags.limit(Tags.Test, 1) // required for integration tests as they can interfere with each other
)

lazy val commonSettings = Seq(
  organization := "io.skuber",
  crossScalaVersions := Seq("2.13.17", "3.3.7"),
  scalaVersion := "3.3.7",
  publishTo :=  {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },
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
  .settings(
    name := "skuber-core",
    commonSettings,
    skuberSettings,
    libraryDependencies ++= Seq(typesafeConfig, scalaTest % Test)
  )

// Skuber Pekko client - concrete Kubernetes Scala client implementation based on Pekko HTTP and Pekko Streams
// Does not have any Akka dependencies

val pekkoGroup = "org.apache.pekko"

val pekkoVersion = "1.3.0"
val pekkoHttpVersion = "1.3.0"

val pekkoSlf4j = pekkoGroup %% "pekko-slf4j" % pekkoVersion
val pekkoHttp = pekkoGroup %% "pekko-http" % pekkoHttpVersion
val pekkoStream = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoStreamTestkit = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoActors = pekkoGroup %% "pekko-actor" % pekkoVersion

lazy val pekkoClientDependencies = Seq(pekkoActors, pekkoHttp, pekkoStream, pekkoSlf4j, logback, pekkoStreamTestkit,
                                       scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test)

lazy val pekko = (project in file("pekko"))
    .settings(name := "skuber-pekko")
    .settings(
      commonSettings,
      libraryDependencies ++= pekkoClientDependencies
    )
    .dependsOn(core)

// Skuber Akka BSL client - concrete Kubernetes Scala client implementation based on Akka HTTP and Akka Streams
// IMPORTANT - the versions of the Akka dependencies in this build of the Skuber Akka client are licensed using BSL
// (see https://www.lightbend.com/akka/license-faq) - please use the Skuber Pekko client unless you are certain you
// understand and accept the implications of the Akka BSL license.

val akkaBSLVersion = "2.8.8"
val akkaBSLHttpVersion = "10.5.3"

val akkaBSLSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaBSLVersion
val akkaBSLHttp = "com.typesafe.akka" %% "akka-http" % akkaBSLHttpVersion
val akkaBSLStream = "com.typesafe.akka" %% "akka-stream" % akkaBSLVersion
val akkaBSLStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaBSLVersion
val akkaBSLActors = "com.typesafe.akka" %% "akka-actor" % akkaBSLVersion

lazy val akkaBSLClientDependencies = Seq(akkaBSLActors, akkaBSLHttp, akkaBSLStream, akkaBSLSlf4j, logback, akkaBSLStreamTestKit,
  scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test)

lazy val akka = (project in file("akka"))
    .settings(
      name := "skuber-akka-bsl",
      commonSettings,
      libraryDependencies ++= akkaBSLClientDependencies
    )
    .dependsOn(core)

lazy val integration = (project in file("integration"))
  .settings(
    publish / skip := true,
    commonSettings,
    libraryDependencies ++= Seq(scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test),
    Test / fork := false
  )
  .dependsOn(core)
  .dependsOn(pekko)  // Always include both clients - there are separate instances of each integration test for each client
  .dependsOn(akka)

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
    .aggregate(core, akka, pekko, integration, examples)

root / publishArtifact := false


