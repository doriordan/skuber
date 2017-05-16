
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val playws = "com.typesafe.play" %% "play-ws" % "2.6.0-M5"
val playtest = "com.typesafe.play" %% "play-test" % "2.6.0-M5"
val playWS = "com.typesafe.play" %% "play-ws" % "2.6.0-M5"
val playAhcWS = "com.typesafe.play" %% "play-ahc-ws" % "2.6.0-M5"
val playAhcWSStandalone = "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0-M6"
val playIteratees = "com.typesafe.play" %% "play-iteratees" % "2.6.1"
val playIterateesReactiveStreams = "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.5"
val specs2 = "org.specs2" %% "specs2-core" % "3.8.9"
val snakeYaml =  "org.yaml" % "snakeyaml" % "1.16"
val commonsIO = "commons-io" % "commons-io" % "2.4"
val playIterateesExtra = "com.typesafe.play.extras" % "iteratees-extras_2.11" % "1.6.0"
val mockws = "de.leanovate.play-mockws" %% "play-mockws" % "2.6.0-M1"


// Akka is required by the examples
val akka ="com.typesafe.akka" %% "akka-actor" % "2.5.1"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
//scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

crossScalaVersions := Seq("2.11.11", "2.12.2")

lazy val commonSettings = Seq(
  organization := "io.doriordan",
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  scalaVersion := "2.11.11",
  publishMavenStyle := false,
  bintrayRepository := "skuber"
)

lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(playws, playIteratees, playIterateesReactiveStreams, playIterateesExtra,
    playWS, playAhcWS, playAhcWSStandalone,
    snakeYaml, commonsIO,
    scalaCheck % Test, specs2 % Test, mockws % Test, playtest % Test).
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

lazy val root = (project in file(".")) aggregate(
  skuber,
  examples)

lazy val skuber= (project in file("client"))
  .enablePlugins(GitVersioning)
  .settings(commonSettings: _*)
  .settings(skuberSettings: _*)

lazy val examples = (project in file("examples"))
  .enablePlugins(GitVersioning)
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)
