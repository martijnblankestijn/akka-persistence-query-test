logLevel := Level.Warn

// Informative Scala compiler errors
addSbtPlugin("com.softwaremill.clippy" % "plugin-sbt" % "0.6.1")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.4.0")
addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.2")

// Display your SBT project's dependency updates.
// Task: dependencyUpdates
// From https://github.com/rtimush/sbt-updates
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.3")

// Format the sources
addSbtPlugin("com.lucidchart" % "sbt-scalafmt-coursier" % "1.14")