import sbt._
import Keys._
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import org.scalastyle.sbt.ScalastylePlugin._
import wartremover._
import wartremover.WartRemover.autoImport.wartremoverErrors


object BuildSettings {

  val wartRemoverSettings = Seq(
    wartremoverErrors in (Compile, compile) ++= Warts.allBut(
      Wart.AsInstanceOf, Wart.Nothing, Wart.Option2Iterable, Wart.ExplicitImplicitTypes,
      Wart.Overloading, Wart.DefaultArguments, Wart.ToString, Wart.Any, Wart.Throw)
  )

  val compileScalastyle = taskKey[Unit]("compileScalastyle")

  val buildSettings = Defaults.coreDefaultSettings ++ Seq (
    organization := "com.drivergrp",
    name         := "core",
    version      := "0.0.1",
    scalaVersion := "2.11.8",
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xlint", "-encoding", "utf8",
      "-language:higherKinds", "-language:implicitConversions", "-language:postfixOps",
      "-Ywarn-infer-any", "-Ywarn-unused", "-Ywarn-unused-import"),
    scalafmtConfig := Some(file(".scalafmt")),
    fork in run := true,
    compileScalastyle := (scalastyle in Compile).toTask("").value,
    (compile in Compile) <<= ((compile in Compile) dependsOn compileScalastyle)
  ) ++ wartRemoverSettings ++ reformatOnCompileSettings
}

object DriverBuild extends Build {
  import BuildSettings._

  val akkaHttpV = "2.4.8"

  val dependencies = Seq(
    "com.typesafe.akka"  %% "akka-http-core" % akkaHttpV,
    "com.typesafe.akka"  %% "akka-http-experimental" % akkaHttpV,
    "com.typesafe.akka"  %% "akka-http-jackson-experimental" % akkaHttpV,
    "com.typesafe.akka"  %% "akka-http-spray-json-experimental" % akkaHttpV,
    "com.typesafe.akka"  %% "akka-http-testkit" % akkaHttpV,
    "org.scalatest"      %  "scalatest_2.11" % "2.2.1" % "test",
    "org.mockito"          % "mockito-core" % "1.9.5" % "test",
    "com.typesafe.slick" %% "slick"  % "3.0.0",
    "com.typesafe"       %  "config" % "1.2.1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback"     % "logback-classic" % "1.1.3",
    "org.slf4j"          % "slf4j-nop"    % "1.6.4",
    "org.scalaz"         %% "scalaz-core" % "7.2.4",
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.7.1",
    "com.lihaoyi" %% "acyclic" % "0.1.4" % "provided"
  )

  lazy val core = Project (
    "core",
    file ("."),
    settings = buildSettings ++ Seq (libraryDependencies ++= dependencies)
  )
}