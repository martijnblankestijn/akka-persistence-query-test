import com.trueaccord.scalapb.compiler.Version.scalapbVersion
import com.typesafe.sbt.packager.docker.Cmd

val akkaVersion = "2.5.6"
val akkaHttpVersion = "10.0.10"



lazy val root = (project in file("."))
  .aggregate(domain, server, query, queryKafka, performancetest, integrationtest)

scalaVersion := "2.12.4"
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

    "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.58",

    "org.iq80.leveldb" % "leveldb" % "0.9",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",

    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    "com.outworkers" %% "phantom-dsl" % "2.7.0",
    "joda-time" % "joda-time" % "2.9.7", // only for Phantom DSL
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "org.typelevel" %% "cats" % "0.9.0",

    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
    "com.google.code.findbugs" % "jsr305" % "2.0.3" // only to prevent error when compiling like [error] Class javax.annotation.CheckReturnValue not found - continuing with a stub.
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
lazy val domain = (project in file("domain"))
  .settings(commonSettings)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

name := "akka-persistence-demo-appointment"
version := "1.0.1"

// for Docker
//packageName in Docker := s"docker-scala-akka-persistence-demo-appointment"
//dockerExposedPorts := Seq(5000)
lazy val persistence = (project in file("persistence"))
  .dependsOn(domain)
  .settings(commonSettings)
lazy val server = (project in file("server"))
  .dependsOn(domain, persistence)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in(Compile, run) := Some("nl.codestar.persistence.Server"),
    packageName in Docker := "akka-persist-server",
    version in Docker := "latest"
  )
lazy val query = (project in file("query"))
  .dependsOn(domain, persistence)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in(Compile, run) := Some("nl.codestar.query.EventProcessorApplication"),
    packageName in Docker := "akka-persist-query",
    maintainer in Docker := "Martijn Blankestijn",
    version in Docker := "latest",
    mappings in Universal += file(s"src/test/docker/lib/wait-for-it/wait-for-it.sh") -> "/bin/wait-for-it.sh",
    dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest",
    dockerCommands := dockerCommands.value.flatMap {
      case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )

lazy val queryKafka = (project in file("query-kafka"))
  .dependsOn(domain, persistence, query)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)
  .settings(
    commonSettings,
    mainClass in(Compile, run) := Some("nl.codestar.query.kafka.EventProcessorApplication"),
    packageName in Docker := "akka-persist-query-kafka",
    maintainer in Docker := "Martijn Blankestijn",
    version in Docker := "latest",
    dockerBaseImage := "frolvlad/alpine-oraclejdk8:latest",
    dockerCommands := dockerCommands.value.flatMap {
      case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
      case other => List(other)
    }
  )

lazy val performancetest = (project in file("performancetest"))
  .enablePlugins(GatlingPlugin)
  .settings(
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2" % "test",
      "io.gatling" % "gatling-test-framework" % "2.2.2" % "test"
    )
  )

lazy val integrationtest = (project in file("integrationtest"))
    .settings(
      libraryDependencies ++= Seq(
        "io.rest-assured" % "rest-assured" % "3.0.6" % "test",
        "io.rest-assured" % "scala-support" % "3.0.6" % "test"
      )
    )
  
