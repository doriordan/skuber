resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

val playws = "com.typesafe.play" %% "play-ws" % "2.4.2"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4"
val specs2 = "org.specs2" %% "specs2-core" % "3.6.2"
val snakeYaml =  "org.yaml" % "snakeyaml" % "1.16"
val commonsIO = "commons-io" % "commons-io" % "2.4"

scalacOptions += "-target:jvm-1.8"

scalacOptions in Test ++= Seq("-Yrangepos")

lazy val commonSettings = Seq(
  organization := "io.doriordan",
  version := "0.1.0",
  scalaVersion := "2.11.6"
)

lazy val root = (project in file(".")) aggregate(
  client,
  examples)

lazy val client = (project in file("client")).
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(playws,snakeYaml,commonsIO,scalaCheck % Test,specs2 % Test)
  )

lazy val examples = (project in file("examples")).
  settings(commonSettings: _*).
  dependsOn(client)

