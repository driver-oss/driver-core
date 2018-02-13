import sbt._
import Keys._

lazy val akkaHttpV = "10.0.11"

lazy val core = (project in file("."))
  .driverLibrary("core")
  .settings(lintingSettings ++ formatSettings)
  .settings(libraryDependencies ++= Seq(
    "xyz.driver"                   %% "tracing"                        % "0.0.2",
    "com.typesafe.akka"            %% "akka-http-core"                 % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-spray-json"           % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-testkit"              % akkaHttpV,
    "com.pauldijou"                %% "jwt-core"                       % "0.14.0",
    "org.scalatest"                %% "scalatest"                      % "3.0.2" % "test",
    "org.scalacheck"               %% "scalacheck"                     % "1.13.4" % "test",
    "org.mockito"                  % "mockito-core"                    % "1.9.5" % "test",
    "com.github.swagger-akka-http" %% "swagger-akka-http"              % "0.11.2",
    "com.amazonaws"                % "aws-java-sdk-s3"                 % "1.11.26",
    "com.google.cloud"             % "google-cloud-pubsub"             % "0.25.0-beta",
    "com.google.cloud"             % "google-cloud-storage"            % "1.7.0",
    "com.typesafe.slick"           %% "slick"                          % "3.2.1",
    "com.typesafe"                 % "config"                          % "1.3.1",
    "com.typesafe.scala-logging"   %% "scala-logging"                  % "3.5.0",
    "eu.timepit"                   %% "refined"                        % "0.8.4",
    "ch.qos.logback"               % "logback-classic"                 % "1.1.11"
  ))
  .settings(version := "1.8.0directives0")
