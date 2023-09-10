
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

// Core/common dependencies
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.15.4"
val specs2 = "org.specs2" %% "specs2-core" % "4.17.0"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.16"
val mockito = "org.mockito" % "mockito-core" % "4.6.1"
val scalaTestMockito = "org.scalatestplus" %% "mockito-4-6" % "3.2.14.0"
val snakeYaml =  "org.yaml" % "snakeyaml" % "2.0"
val commonsIO = "commons-io" % "commons-io" % "2.11.0"
val commonsCodec = "commons-codec" % "commons-codec" % "1.15"
val logback = "ch.qos.logback" % "logback-classic" % "1.4.4" % Runtime
val playJson = "com.typesafe.play" %% "play-json" % "2.9.3"
val typesafeConfig = "com.typesafe" % "config" % "1.4.2"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

ThisBuild / version := "2.6.7"

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
  crossScalaVersions := Seq("2.12.17", "2.13.10"),
  scalaVersion := "2.13.10",
  publishTo :=  sonatypePublishToBundle.value,
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
)

// skuber core - contains the skuber model and core API - has no dependencies on Akka/Pekko
lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(
    playJson, snakeYaml, commonsIO, commonsCodec,
    scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test,
    scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)
lazy val skuberCore = (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    skuberSettings,
    libraryDependencies ++= Seq(typesafeConfig, scalaTest % "it")
  )

// Skuber Akka client - concrete Kubernetes Scala client implementation based on Akka HTTP and Akka Streams

val akkaVersion = "2.6.19"
val akkaHttpVersion = "10.2.9"
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion
val akkaActors = "com.typesafe.akka" %% "akka-actor" % akkaVersion
lazy val akkaClientDependencies = Seq(akkaActors, akkaHttp, akkaStream, akkaSlf4j, logback, scalaTest % "it")
lazy val skuberAkkaClient = (project in file("akka"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies ++= akkaClientDependencies
  )
  .dependsOn(skuberCore)

// Skuber Pekko client - concrete Kubernetes Scala client implementation based on Pekko HTTP and Pekko Streams
// Does not have any Akka dependencies

val pekko = "org.apache.pekko"
val pekkoActorsVersion = "1.0.1"
val pekkoStreamVersion = "1.0.0"
val pekkoHttpVersion = "1.0.0"
val pekkoSlf4jVersion = "1.0.1"
val pekkoSlf4j = pekko %% "pekko-slf4j" % pekkoSlf4jVersion
val pekkoHttp = pekko %% "pekko-http" % pekkoHttpVersion
val pekkoStream = pekko %% "pekko-stream-testkit" % pekkoStreamVersion
val pekkoStreamTestkit = pekko %% "pekko-stream-testkit" % pekkoStreamVersion
val pekkoActors = pekko %% "pekko-actor" % pekkoActorsVersion
lazy val pekkoClientDependencies = Seq(pekkoActors, pekkoHttp, pekkoStream, pekkoSlf4j, logback, pekkoStreamTestkit, scalaTest % "it")
lazy val skuberPekkoClient = (project in file("pekko"))
    .configs(IntegrationTest)
    .settings(
      commonSettings,
      skuberSettings,
      Defaults.itSettings,
      libraryDependencies ++= pekkoClientDependencies
    )
    .dependsOn(skuberCore)

// Examples project
lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(akkaActors, akkaSlf4j, logback)
)
// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  assembly /mainClass := Some("skuber.examples.guestbook.Guestbook")
)
lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuberCore)
  .dependsOn(skuberAkkaClient) // TODO migrate examples to Pekko client

lazy val root = (project in file("."))
    .settings(commonSettings: _*)
    .aggregate(skuberCore, skuberAkkaClient, skuberPekkoClient, examples)

root / publishArtifact := false


