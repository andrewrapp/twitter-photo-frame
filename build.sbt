ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.17"

lazy val root = project
  .in(file("."))
  .settings(
    name := "twitter-photo-frame",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "com.twitter" %% "finagle-http" % "22.4.0",
      "com.twitter" %% "twitter-server" % "22.4.0",
      "com.twitter" %% "finagle-stats" % "22.4.0",
      "com.twitter" %% "finatra-jackson" % "22.4.0",
      "com.twitter" %% "inject-server" % "22.4.0",
      "com.twitter" %% "inject-app" % "22.4.0",
      "com.twitter" %% "inject-core" % "22.4.0",
      "com.twitter" %% "inject-modules" % "22.4.0",
      "com.twitter" %% "util-slf4j-api" % "22.4.0",
      "ch.qos.logback" % "logback-classic" % "1.1.3"
    )
  )