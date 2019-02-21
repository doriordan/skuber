
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
val specs2 = "org.specs2" %% "specs2-core" % "4.4.1"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
val mockito = "org.mockito" % "mockito-core" % "2.24.5"
val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.21"

val snakeYaml =  "org.yaml" % "snakeyaml" % "1.23"
val commonsIO = "commons-io" % "commons-io" % "2.6"
val commonsCodec = "commons-codec" % "commons-codec" % "1.12"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.61"

// the client API request/response handing uses Akka Http 
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.1.7"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % "2.5.21"
val akka = "com.typesafe.akka" %% "akka-actor" % "2.5.21"

// Skuber uses akka logging, so the examples config uses the akka slf4j logger with logback backend
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.5.21"
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.7.1"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

version in ThisBuild := "2.1.0"

sonatypeProfileName := "io.skuber"

publishMavenStyle in ThisBuild := true

licenses in ThisBuild := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage in ThisBuild := Some(url("https://github.com/doriordan"))

scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/doriordan/skuber"),
    "scm:git@github.com:doriordan/skuber.git"
  )
)

developers in ThisBuild := List(Developer(id="doriordan", name="David ORiordan", email="doriordan@gmail.com", url=url("https://github.com/doriordan")))

lazy val commonSettings = Seq(
  organization := "io.skuber",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomIncludeRepository := { _ => false }
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
  mainClass in assembly := Some("skuber.examples.guestbook.Guestbook"),
  assemblyMergeStrategy in assembly := {
    case PathList("module-info.class") =>
      MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

publishArtifact in root := false

lazy val root = (project in file(".")) 
  .settings(commonSettings: _*)
  .aggregate(skuber, examples)
  .disablePlugins(AssemblyPlugin)

lazy val skuber= (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies += scalaTest % "it"
  )
  .disablePlugins(AssemblyPlugin)

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)
  .enablePlugins(AssemblyPlugin)
