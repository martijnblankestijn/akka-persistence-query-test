logLevel := Level.Warn

// Informative Scala compiler errors
addSbtPlugin("com.softwaremill.clippy" % "plugin-sbt" % "0.5.2")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.5")
addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.1")
