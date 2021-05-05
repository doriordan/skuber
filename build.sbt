import xerial.sbt.Sonatype._
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

val akkaVersion = "2.6.8"

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.3"
val specs2 = "org.specs2" %% "specs2-core" % "4.8.3"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"
val mockito = "org.mockito" % "mockito-core" % "3.4.4"
val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion

val snakeYaml =  "org.yaml" % "snakeyaml" % "1.28"
val commonsIO = "commons-io" % "commons-io" % "2.7"
val commonsCodec = "commons-codec" % "commons-codec" % "1.14"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.66"

// the client API request/response handing uses Akka Http
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.12"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion

// Skuber uses akka logging, so the examples config uses the akka slf4j logger with logback backend
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val logback = "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.9.0"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

version in ThisBuild := "2.7.0"

sonatypeProfileName := "io.github.hagay3"

publishMavenStyle in ThisBuild := true

licenses in ThisBuild := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage in ThisBuild := Some(url("https://github.com/hagay3"))

sonatypeCredentialHost in ThisBuild := "s01.oss.sonatype.org"
sonatypeRepository in ThisBuild := "https://s01.oss.sonatype.org/service/local"


sonatypeProjectHosting := Some(GitHubHosting("hagay3", "skuber", "hagay3@gmail.com"))

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/hagay3/skuber"),
    "scm:git@github.com:hagay3/skuber.git"
  )
)

developers in ThisBuild := List(Developer(id="hagay3", name="Hagai Ovadia", email="hagay3@gmail.com", url=url("https://github.com/hagay3")))

lazy val commonSettings = Seq(
  organization := "io.github.hagay3",
  crossScalaVersions := Seq("2.12.10", "2.13.3"),
  scalaVersion := "2.12.10",
  publishTo := {
    val nexus = "https://s01.oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
)

lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(
    akkaHttp, akkaStream, playJson, snakeYaml, commonsIO, commonsCodec, bouncyCastle,
    scalaCheck % Test, specs2 % Test, mockito % Test, akkaStreamTestKit % Test,
    scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)

lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(akka, akkaSlf4j, logback)
)

// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  mainClass in assembly := Some("skuber.examples.guestbook.Guestbook")
)

publishArtifact in root := false

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(skuber, examples)

lazy val skuber= (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies += scalaTest % "it"
  )

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)
