
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val playws = "com.typesafe.play" %% "play-ws" % "2.4.8"
val playtest = "com.typesafe.play" %% "play-test" % "2.4.8"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4"
val specs2 = "org.specs2" %% "specs2-core" % "3.7"
val snakeYaml =  "org.yaml" % "snakeyaml" % "1.16"
val commonsIO = "commons-io" % "commons-io" % "2.4"
val playIterateesExtra = "com.typesafe.play.extras" %% "iteratees-extras" % "1.5.0"
val mockws = "de.leanovate.play-mockws" %% "play-mockws" % "2.4.2"


// Akka is required by the examples
val akka ="com.typesafe.akka" %% "akka-actor" % "2.4.0"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

version in ThisBuild := "1.7.1-RC2"

// NOTE: not the long-term planned profile name or organization
sonatypeProfileName := "io.github.doriordan"

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
  organization := "io.github.doriordan",
  scalaVersion := "2.11.8",
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
  libraryDependencies ++= Seq(playws,playIterateesExtra,snakeYaml,commonsIO,scalaCheck % Test,specs2 % Test, mockws % Test, playtest % Test).
				map(_.exclude("commons-logging","commons-logging"))
)

lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies += akka
)

// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  mainClass in assembly := Some("skuber.examples.guestbook.Guestbook")
)

publishArtifact in root := false

lazy val root = (project in file(".")) aggregate(
  skuber,
  examples)

lazy val skuber= (project in file("client"))
  .settings(commonSettings: _*)
  .settings(skuberSettings: _*)

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)
