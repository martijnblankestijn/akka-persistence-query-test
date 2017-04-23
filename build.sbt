import com.trueaccord.scalapb.compiler.Version.scalapbVersion

name := "akka-persistence-demo-appointment"

version := "1.0.1"
scalaVersion := "2.12.2"

val akkaVersion = "2.5.0"
val akkaHttpVersion = "10.0.5"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion, // needed for logging filter akka.event.slf4j.Slf4jLoggingFilter
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion, // query-side of cqrs
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.51",
  "org.iq80.leveldb" % "leveldb" % "0.9",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
  "org.scala-lang.modules" % "scala-java8-compat_2.11" % "0.8.0",
  "com.outworkers" %% "phantom-dsl" % "2.7.0",
  "joda-time" % "joda-time" % "2.9.7",  // only for Phantom DSL
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "org.typelevel" %% "cats" % "0.9.0",

  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test",
  "com.google.code.findbugs" % "jsr305" % "2.0.3" // only to prevent error when compiling like [error] Class javax.annotation.CheckReturnValue not found - continuing with a stub.
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)


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
// To look at
// , "-Xlint"
//   "-Ywarn-unused-import", // disabled dure to errors in generated code from protobuf


// mainClass in (Compile, run) := Some("nl.codestar.query.ReadJournalClient")
