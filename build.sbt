import xerial.sbt.Sonatype._
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

val scala12Version = "2.12.13"
val scala13Version = "2.13.6"
val currentScalaVersion = scala13Version
val supportedScalaVersion = Seq(scala12Version, scala13Version)

val akkaVersion = "2.6.19"


val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.15.4"

val specs2 = "org.specs2" %% "specs2-core" % "4.12.12"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.9"

val mockito = "org.mockito" % "mockito-core" % "3.12.4"

val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion


val snakeYaml =  "org.yaml" % "snakeyaml" % "1.29"

val commonsIO = "commons-io" % "commons-io" % "2.11.0"
val commonsCodec = "commons-codec" % "commons-codec" % "1.15"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.69"


// the client API request/response handing uses Akka Http
val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.2.9"
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val akka = "com.typesafe.akka" %% "akka-actor" % akkaVersion

// Skuber uses akka logging, so the examples config uses the akka slf4j logger with logback backend
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val logback = "ch.qos.logback" % "logback-classic" % "1.2.11" % Runtime

// the Json formatters are based on Play Json
val playJson = "com.typesafe.play" %% "play-json" % "2.9.2"

// Need Java 8 or later as the java.time package is used to represent K8S timestamps
scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

sonatypeProfileName := "io.github.hagay3"

ThisBuild / publishMavenStyle := true

ThisBuild / licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ThisBuild / homepage := Some(url("https://github.com/hagay3"))

publishTo := sonatypePublishToBundle.value
sonatypeCredentialHost := Sonatype.sonatype01
updateOptions in ThisBuild := updateOptions.value.withGigahorse(false)



sonatypeProjectHosting := Some(GitHubHosting("hagay3", "skuber", "hagay3@gmail.com"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/hagay3/skuber"),
    "scm:git@github.com:hagay3/skuber.git"
  )
)

ThisBuild / developers  := List(Developer(id="hagay3", name="Hagai Ovadia", email="hagay3@gmail.com", url=url("https://github.com/hagay3")))

lazy val commonSettings = Seq(
  organization := "io.github.hagay3",
  scalaVersion := currentScalaVersion,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
  sonatypeCredentialHost := Sonatype.sonatype01
)
// run sbt githubWorkflowGenerate in order to generate github actions files
inThisBuild(List(
  githubWorkflowScalaVersions := supportedScalaVersion,
  githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
  githubWorkflowTargetTags ++= Seq("v*"),
  githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "It/compile"))),
  githubWorkflowPublish := Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}")))))


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
  assembly / mainClass := Some("skuber.examples.guestbook.Guestbook")
)

root / publishArtifact := false

lazy val root = (project in file("."))
  .settings(commonSettings,
    crossScalaVersions := Nil)
  .aggregate(skuber, examples)

lazy val skuber= (project in file("client"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    crossScalaVersions := supportedScalaVersion,
    skuberSettings,
    Defaults.itSettings,
    libraryDependencies += scalaTest % "it"
  )

lazy val examples = (project in file("examples"))
  .settings(
    commonSettings,
    crossScalaVersions := supportedScalaVersion)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(skuber)