
resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

// Core/common dependencies
val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.19.0"
val specs2 = "org.specs2" %% "specs2-core" % "4.23.0"
val scalaTest = "org.scalatest" %% "scalatest" % "3.2.20"
val mockito = "org.mockito" % "mockito-core" % "5.23.0"
val scalaTestMockito = "org.scalatestplus" %% "mockito-5-18" % "3.2.19.0"
val snakeYaml =  "org.yaml" % "snakeyaml" % "2.6"
val commonsCodec = "commons-codec" % "commons-codec" % "1.22.0"
val commonsIO = "commons-io" % "commons-io" % "2.22.0"
val typesafeConfig = "com.typesafe" % "config" % "1.4.9"
val logback = "ch.qos.logback" % "logback-classic" % "1.5.34" % Runtime
val playJson = "org.playframework" %% "play-json" % "3.0.6"

scalacOptions += "-target:jvm-1.8"

Test / scalacOptions ++= Seq("-Yrangepos")

ThisBuild / version := "3.2.1"

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

Global / concurrentRestrictions := Seq(
  Tags.limit(Tags.Test, 1) // required for integration tests as they can interfere with each other
)

lazy val commonSettings = Seq(
  organization := "io.skuber",
  crossScalaVersions := Seq("3.9.0", "3.3.7"),
  scalaVersion := "3.3.7",
  publishTo :=  {
    val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
    if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
    else localStaging.value
  },
  pomIncludeRepository := { _ => false },
  Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
)

// skuber core - contains the skuber model and core API - has no dependencies on Akka/Pekko
lazy val skuberSettings = Seq(
  libraryDependencies ++= Seq(
    playJson, snakeYaml, commonsIO, commonsCodec,
    scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test,
    scalaTest % Test
  ).map(_.exclude("commons-logging", "commons-logging"))
)

lazy val core = (project in file("core"))
  .settings(
    name := "skuber-core",
    commonSettings,
    skuberSettings,
    libraryDependencies ++= Seq(typesafeConfig, scalaTest % Test)
  )

// Skuber Pekko client - concrete Kubernetes Scala client implementation based on Pekko HTTP and Pekko Streams
// Does not have any Akka dependencies

val pekkoGroup = "org.apache.pekko"

val pekkoVersion = "1.6.0"
val pekkoHttpVersion = "1.3.0"

val pekkoSlf4j = pekkoGroup %% "pekko-slf4j" % pekkoVersion
val pekkoHttp = pekkoGroup %% "pekko-http" % pekkoHttpVersion
val pekkoStream = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoStreamTestkit = pekkoGroup %% "pekko-stream-testkit" % pekkoVersion
val pekkoActors = pekkoGroup %% "pekko-actor" % pekkoVersion

lazy val pekkoClientDependencies = Seq(pekkoActors, pekkoHttp, pekkoStream, pekkoSlf4j, logback, pekkoStreamTestkit % Test,
                                       scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test)

lazy val pekko = (project in file("pekko"))
    .settings(name := "skuber-pekko")
    .settings(
      commonSettings,
      libraryDependencies ++= pekkoClientDependencies
    )
    .dependsOn(core)

// Skuber Akka BSL client - concrete Kubernetes Scala client implementation based on Akka HTTP and Akka Streams
// IMPORTANT - the versions of the Akka dependencies in this build of the Skuber Akka client are licensed using BSL
// (see https://www.lightbend.com/akka/license-faq) - please use the Skuber Pekko client unless you are certain you
// understand and accept the implications of the Akka BSL license.

val akkaBSLVersion = "2.8.8"
val akkaBSLHttpVersion = "10.5.3"

val akkaBSLSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaBSLVersion
val akkaBSLHttp = "com.typesafe.akka" %% "akka-http" % akkaBSLHttpVersion
val akkaBSLStream = "com.typesafe.akka" %% "akka-stream" % akkaBSLVersion
val akkaBSLStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaBSLVersion
val akkaBSLActors = "com.typesafe.akka" %% "akka-actor" % akkaBSLVersion

lazy val akkaBSLClientDependencies = Seq(akkaBSLActors, akkaBSLHttp, akkaBSLStream, akkaBSLSlf4j, logback, akkaBSLStreamTestKit % Test,
  scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test)

lazy val akka = (project in file("akka"))
    .settings(
      name := "skuber-akka-bsl",
      commonSettings,
      libraryDependencies ++= akkaBSLClientDependencies
    )
    .dependsOn(core)

lazy val integration = (project in file("integration"))
  .settings(
    publish / skip := true,
    commonSettings,
    libraryDependencies ++= Seq(scalaCheck % Test, specs2 % Test, mockito % Test, scalaTestMockito % Test, scalaTest % Test),
    Test / fork := false
  )
  .dependsOn(core)
  .dependsOn(pekko)  // Always include both clients - there are separate instances of each integration test for each client
  .dependsOn(akka)

// Examples project
lazy val examplesSettings = Seq(
  name := "skuber-examples",
  libraryDependencies ++= Seq(pekkoActors, pekkoSlf4j, logback)
)

// by default run the guestbook example when executing a fat examples JAR
lazy val examplesAssemblySettings = Seq(
  assembly /mainClass := Some("skuber.examples.guestbook.Guestbook")
)

lazy val examples = (project in file("examples"))
  .settings(commonSettings: _*)
  .settings(examplesSettings: _*)
  .settings(examplesAssemblySettings: _*)
  .dependsOn(core)
  .dependsOn(pekko)

// Ammonite REPL with skuber Pekko client pre-initialized
// Usage: ./repl/amm        (first run builds classpath via sbt automatically)
//        ./repl/amm --refresh  (force classpath rebuild after code changes)
// Ammonite version must match the project's Scala version (3.3.7).
// Check https://github.com/com-lihaoyi/Ammonite/releases for compatible versions.
val ammVersion = "3.0.9"
val exportReplClasspath = taskKey[File]("Export repl classpath to repl/.classpath for use by the amm shell script")

lazy val repl = (project in file("repl"))
  .settings(
    name := "skuber-repl",
    publish / skip := true,
    scalaVersion := "3.3.7",
    libraryDependencies += "com.lihaoyi" % "ammonite" % ammVersion cross CrossVersion.full,
    exportReplClasspath := {
      val cp = (Compile / fullClasspath).value.files.mkString(java.io.File.pathSeparator)
      val cpFile = baseDirectory.value / ".classpath"
      IO.write(cpFile, cp)
      streams.value.log.info(s"Classpath written to ${cpFile.getAbsolutePath}")
      cpFile
    }
  )
  .dependsOn(pekko)

// Skuber Cats Effect client - concrete Kubernetes Scala client implementation based on Cats Effect, fs2 and http4s
// Scala 3 only

val catsEffectVersion = "3.7.1"
val fs2Version = "3.13.0"
val http4sVersion = "0.23.34"

val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectVersion
val fs2Core = "co.fs2" %% "fs2-core" % fs2Version
val fs2IO = "co.fs2" %% "fs2-io" % fs2Version
val http4sClient = "org.http4s" %% "http4s-client" % http4sVersion
val http4sDsl = "org.http4s" %% "http4s-dsl" % http4sVersion
val http4sEmberClient = "org.http4s" %% "http4s-ember-client" % http4sVersion
val http4sJdkHttpClient = "org.http4s" %% "http4s-jdk-http-client" % "0.10.0"

val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % "2.2.0"

val zioVersion     = "2.1.26"
val zioHttpVersion = "3.11.4"

lazy val zioClientDependencies = Seq(
  "dev.zio" %% "zio"          % zioVersion,
  "dev.zio" %% "zio-streams"  % zioVersion,
  "dev.zio" %% "zio-http"     % zioHttpVersion,
  "dev.zio" %% "zio-test"     % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
)

lazy val catsClientDependencies = Seq(
  catsEffect, fs2Core, fs2IO,
  http4sClient, http4sDsl, http4sEmberClient, http4sJdkHttpClient,
  scalaTest % Test,
  munitCatsEffect % Test
)

lazy val cats = (project in file("cats"))
  .settings(
    name := "skuber-cats",
    organization := "io.skuber",
    scalaVersion := "3.3.7",
    crossScalaVersions := Seq("3.3.7"),
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    pomIncludeRepository := { _ => false },
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    testFrameworks += new TestFramework("munit.Framework"),
    libraryDependencies ++= catsClientDependencies
  )
  .dependsOn(core)

// Integration tests for the Cats Effect client (Scala 3 only, separate subproject per SBT 1.9+ guidance)
lazy val `cats-it` = (project in file("cats-it"))
  .settings(
    name := "skuber-cats-it",
    publish / skip := true,
    scalaVersion := "3.3.7",
    crossScalaVersions := Seq("3.3.7"),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    testFrameworks += new TestFramework("munit.Framework"),
    Test / parallelExecution := false,
    Test / fork := false,
    libraryDependencies ++= Seq(
      munitCatsEffect % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.29" % Test
    )
  )
  .dependsOn(cats)

lazy val zio = (project in file("zio"))
  .settings(
    name := "skuber-zio",
    organization := "io.skuber",
    scalaVersion := "3.3.7",
    crossScalaVersions := Seq("3.3.7"),
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    pomIncludeRepository := { _ => false },
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= zioClientDependencies
  )
  .dependsOn(core)

lazy val `zio-it` = (project in file("zio-it"))
  .settings(
    name := "skuber-zio-it",
    organization := "io.skuber",
    publish / skip := true,
    scalaVersion := "3.3.7",
    crossScalaVersions := Seq("3.3.7"),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / parallelExecution := false,
    Test / fork := false,
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-test"     % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.5.34" % Test
    )
  )
  .dependsOn(`zio`)

lazy val root = (project in file("."))
    .settings(
      publish / skip := true,
      commonSettings
    )
    .aggregate(core, akka, pekko, cats, `cats-it`, zio, `zio-it`, integration, examples, repl)

root / publishArtifact := false


