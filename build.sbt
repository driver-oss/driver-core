import sbt._
import Keys._

lazy val akkaHttpV = "10.0.5"
lazy val googleTraceV = "0.5.0"

lazy val core = (project in file("."))
  .driverLibrary("core")
  .settings(lintingSettings ++ formatSettings)
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka"            %% "akka-http-core"                 % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-spray-json"           % akkaHttpV,
    "com.typesafe.akka"            %% "akka-http-testkit"              % akkaHttpV,
    "com.pauldijou"                %% "jwt-core"                       % "0.14.0",
    "org.scalatest"                %% "scalatest"                      % "3.0.1" % "test",
    "org.scalacheck"               %% "scalacheck"                     % "1.13.4" % "test",
    "org.mockito"                  % "mockito-core"                    % "1.9.5" % "test",
    "com.github.swagger-akka-http" %% "swagger-akka-http"              % "0.9.1",
    "com.amazonaws"                % "aws-java-sdk-s3"                 % "1.11.26",
    "com.google.cloud"             % "google-cloud-pubsub"             % "0.25.0-beta",
    "com.google.cloud"             % "google-cloud-storage"            % "1.7.0",
    "com.typesafe.slick"           %% "slick"                          % "3.2.1",
    "com.typesafe"                 % "config"                          % "1.2.1",
    "com.typesafe.scala-logging"   %% "scala-logging"                  % "3.5.0",
    "ch.qos.logback"               % "logback-classic"                 % "1.1.3",
    "com.google.cloud.trace"       % "core"                            % googleTraceV,
    "com.google.cloud.trace"       % "logging-service"                 % googleTraceV,
    "com.google.cloud.trace"       % "trace-grpc-api-service"          % googleTraceV,
    // the following version of netty boringssl (or maybe greater) is/was needed to avoid Jetty ALPN/NPN
    // config errors w/ SSL in google libs. Before removing test that tracing posts to google
    // in a service that uses this library.
    "io.netty"                     % "netty-tcnative-boringssl-static" % "2.0.3.Final"
  ))
