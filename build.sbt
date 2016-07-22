import sbt._
import Keys._
import DriverConfigurations._


lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val buildSettings = Defaults.coreDefaultSettings ++ Seq (
  organization := "com.drivergrp",
  name         := "core",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-encoding", "utf8",
    "-language:higherKinds", "-language:implicitConversions", "-language:postfixOps",
    "-Ywarn-infer-any", "-Ywarn-unused", "-Ywarn-unused-import"),
  fork in run := true,
  compileScalastyle := (scalastyle in Compile).toTask("").value,
  (compile in Compile) <<= ((compile in Compile) dependsOn compileScalastyle)
) ++ wartRemoverSettings ++ DriverConfigurations.scalafmtSettings

lazy val akkaHttpV = "2.4.8"

lazy val core = (project in file(".")).
  settings(buildSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-http-core" % akkaHttpV,
      "com.typesafe.akka"  %% "akka-http-experimental" % akkaHttpV,
      "com.typesafe.akka"  %% "akka-http-jackson-experimental" % akkaHttpV,
      "com.typesafe.akka"  %% "akka-http-spray-json-experimental" % akkaHttpV,
      "com.typesafe.akka"  %% "akka-http-testkit" % akkaHttpV,
      "org.scalatest"      %  "scalatest_2.11" % "2.2.1" % "test",
      "org.mockito"          % "mockito-core" % "1.9.5" % "test",
      "com.typesafe.slick" %% "slick"  % "3.1.1",
      "com.typesafe"       %  "config" % "1.2.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
      "ch.qos.logback"     % "logback-classic" % "1.1.3",
      "org.slf4j"          % "slf4j-nop"    % "1.6.4",
      "org.scalaz"         %% "scalaz-core" % "7.2.4",
      "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.7.1",
      "com.lihaoyi" %% "acyclic" % "0.1.4" % "provided"
    ))
  .gitPluginConfiguration
  .settings(publicationSettings() ++ releaseSettings())
