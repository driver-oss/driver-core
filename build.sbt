import sbt._
import Keys._

lazy val akkaHttpV = "10.0.5"

lazy val core = (project in file("."))
  .driverLibrary("core")
  .settings(lintingSettings ++ formatSettings)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka"            %% "akka-http-core"       % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-testkit"    % akkaHttpV,
    "org.scalatest"                % "scalatest_2.11"        % "2.2.6" % "test",
    "org.scalacheck"               %% "scalacheck"           % "1.12.5" % "test",
    "org.mockito"                  % "mockito-core"          % "1.9.5" % "test",
    "com.github.swagger-akka-http" %% "swagger-akka-http"    % "0.9.1",
    "com.amazonaws"                % "aws-java-sdk-s3"       % "1.11.26",
    "com.google.cloud"             % "google-cloud-storage"  % "0.9.4-beta",
    "com.typesafe.slick"           %% "slick"                % "3.1.1",
    "com.typesafe"                 % "config"                % "1.2.1",
    "com.typesafe.scala-logging"   %% "scala-logging"        % "3.4.0",
    "ch.qos.logback"               % "logback-classic"       % "1.1.3"
  ))
