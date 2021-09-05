
ThisBuild / evictionErrorLevel := Level.Warn

// for tests
libraryDependencies += ("org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.1.0")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.7.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.4")
