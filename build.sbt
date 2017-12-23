import com.trueaccord.scalapb.compiler.Version.scalapbVersion
import com.typesafe.sbt.packager.docker.Cmd

name := "akka-persistence-demo-appointment"
version := "1.0.1"
scalaVersion := "2.12.4"

val akkaVersion = "2.5.8"
val akkaHttpVersion = "10.0.11"
val akkaPersistenceCassandraVersion = "0.80-RC3"
val phantomDslVersion = "2.16.4"
val catsVersion = "1.0.0-RC2"
val gatlingVersion = "2.3.0"
val scalaTestVersion = "3.0.4"

// run scalafmt automatically before compiling for all projects
scalafmtOnCompile in ThisBuild := true

lazy val root = (project in file("."))
  .aggregate(domain,
             appointments,
             api,
             query,
             queryKafka,
             performancetest,
             integrationtest)

lazy val commonSettings = Seq(
  organization := "Ordina Codestar",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.12.4",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion, // needed for logging filter akka.event.slf4j.Slf4jLoggingFilter
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion, // query-side of cqrs
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    "com.outworkers" %% "phantom-dsl" % phantomDslVersion,
    "com.outworkers" %% "phantom-jdk8" % phantomDslVersion,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
    "com.google.code.findbugs" % "jsr305" % "3.0.2" // only to prevent error when compiling like [error] Class javax.annotation.CheckReturnValue not found - continuing with a stub.
  ),
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ywarn-dead-code",
    "-Ywarn-infer-any",
    "-Xfatal-warnings"
  )
)
lazy val domain =
  project // if the name of the val is the same as the directory, you can just say 'project'
    .settings(commonSettings)
    .settings(
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
      )
    )

lazy val appointments = project
  .settings(commonSettings)
  .dependsOn(domain)

// for Docker
//packageName in Docker := s"docker-scala-akka-persistence-demo-appointment"
//dockerExposedPorts := Seq(5000)
lazy val persistence = project
  .dependsOn(domain)
  .settings(commonSettings)

lazy val api = project
  .dependsOn(domain, persistence, appointments)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in (Compile, run) := Some("nl.codestar.persistence.Server"),
    packageName in Docker := "akka-persist-server",
    version in Docker := "latest"
  )

lazy val query = project
  .dependsOn(domain, persistence)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in (Compile, run) := Some(
      "nl.codestar.query.EventProcessorApplication"),
    packageName in Docker := "akka-persist-query",
    maintainer in Docker := "Martijn Blankestijn",
    version in Docker := "latest",
    mappings in Universal += file(
      s"src/test/docker/lib/wait-for-it/wait-for-it.sh") -> "/bin/wait-for-it.sh",
    dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest",
    dockerCommands := dockerCommands.value.flatMap {
      case cmd @ Cmd("FROM", _) =>
        List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )

lazy val queryKafka = (project in file("query-kafka"))
  .dependsOn(domain, persistence, query)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in (Compile, run) := Some(
      "nl.codestar.query.kafka.EventProcessorApplication"),
    packageName in Docker := "akka-persist-query-kafka",
    maintainer in Docker := "Martijn Blankestijn",
    version in Docker := "latest",
    dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest",
    dockerCommands := dockerCommands.value.flatMap {
      case cmd @ Cmd("FROM", _) =>
        List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )

lazy val performancetest = project
  .enablePlugins(GatlingPlugin)
  .settings(
    scalaVersion := "2.12.4",
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
      "io.gatling" % "gatling-test-framework" % gatlingVersion % "test"
    )
  )

lazy val integrationtest = project
  .settings(
    scalaVersion := "2.12.4",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
      "io.rest-assured" % "rest-assured" % "3.0.6" % "test",
      "io.rest-assured" % "scala-support" % "3.0.6" % "test"
    )
  )
