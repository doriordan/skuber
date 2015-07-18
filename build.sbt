resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

val playws = "com.typesafe.play" %% "play-ws" % "2.4.2"
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4"
val specs2 = "org.specs2" %% "specs2-core" % "3.6.2"

scalacOptions in Test ++= Seq("-Yrangepos")

lazy val commonSettings = Seq(
  organization := "io.doriordan",
  version := "0.1.0",
  scalaVersion := "2.11.6"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "skuber",
    version := "0.10",
    libraryDependencies ++= Seq(playws,scalaCheck % Test,specs2 % Test)
  )


