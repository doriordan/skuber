
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.5"
val specs2 = "org.specs2" %% "specs2-core" % "3.9.5"

val snakeYaml =  "org.yaml" % "snakeyaml" % "1.16"
val commonsIO = "commons-io" % "commons-io" % "2.5"
val commonsCodec = "commons-codec" % "commons-codec" % "1.10"
val sl4j = "org.slf4j" % "slf4j-api" % "1.7.25"

// the client API request/response handing uses Akka Http 
// This also brings in the transitive dependencies on Akka actors and streams
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.10"

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.6.6"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

version in ThisBuild := "2.0.0-RC2"

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
  scalaVersion := "2.12.3",
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
  libraryDependencies ++= Seq(akkaHttp, playJson, snakeYaml, commonsIO, commonsCodec, sl4j, scalaCheck % Test,specs2 % Test).
				map(_.exclude("commons-logging","commons-logging"))
)

lazy val examplesSettings = Seq(
  name := "skuber-examples"
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
  .settings(commonSettings: _*)
  .settings(skuberSettings: _*)

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)
