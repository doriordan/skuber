
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

val playws = "com.typesafe.play" %% "play-ws" % "2.4.8"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4"
val specs2 = "org.specs2" %% "specs2-core" % "3.7"
val snakeYaml =  "org.yaml" % "snakeyaml" % "1.16"
val commonsIO = "commons-io" % "commons-io" % "2.4"
val playIterateesExtra = "com.typesafe.play.extras" %% "iteratees-extras" % "1.5.0"

// Akka is required by the examples
val akka ="com.typesafe.akka" %% "akka-actor" % "2.4.0"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")


lazy val commonSettings = Seq(
  organization := "io.doriordan",
  scalaVersion := "2.11.8",
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  publishMavenStyle := false,
  bintrayRepository := "skuber"
)

lazy val skuberSettings = Seq(
  name := "skuber",
  libraryDependencies ++= Seq(playws,playIterateesExtra,snakeYaml,commonsIO,scalaCheck % Test,specs2 % Test).
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
