import sbt._
import Keys._

lazy val akkaHttpV = "10.1.1"

lazy val core = (project in file("."))
  .driverLibrary("core")
  .settings(lintingSettings ++ formatSettings)
  .settings(libraryDependencies ++= Seq(
    "xyz.driver"                    %% "tracing"                        % "0.1.2",
    "com.typesafe.akka"             %% "akka-actor"                     % "2.5.13",
    "com.typesafe.akka"             %% "akka-stream"                    % "2.5.13",
    "com.typesafe.akka"             %% "akka-http-core"                 % akkaHttpV,
    "com.typesafe.akka"             %% "akka-http-spray-json"           % akkaHttpV,
    "com.typesafe.akka"             %% "akka-http-testkit"              % akkaHttpV,
    "com.pauldijou"                 %% "jwt-core"                       % "0.16.0",
    "io.kamon"                      %% "kamon-core"                     % "1.1.3",
    "io.kamon"                      %% "kamon-statsd"                   % "1.0.0",
    "io.kamon"                      %% "kamon-system-metrics"           % "1.0.0",
    "io.kamon"                      %% "kamon-akka-2.5"                 % "1.0.0",
    "org.scalatest"                 %% "scalatest"                      % "3.0.5" % "test",
    "org.scalacheck"                %% "scalacheck"                     % "1.14.0" % "test",
    "org.scalaz"                    %% "scalaz-core"                    % "7.2.24",
    "com.github.swagger-akka-http"  %% "swagger-akka-http"              % "0.14.0",
    "com.typesafe.scala-logging"    %% "scala-logging"                  % "3.9.0",
    "eu.timepit"                    %% "refined"                        % "0.9.0",
    "com.typesafe.slick"            %% "slick"                          % "3.2.3",
    "com.beachape"                  %% "enumeratum"                     % "1.5.13",
    "org.mockito"                   %  "mockito-core"                   % "1.9.5"       % Test,
    "com.amazonaws"                 %  "aws-java-sdk-s3"                % "1.11.342",
    "com.google.cloud"              %  "google-cloud-pubsub"            % "1.31.0",
    "com.google.cloud"              %  "google-cloud-storage"           % "1.31.0",
    "com.aliyun.oss"                %  "aliyun-sdk-oss"                 % "2.8.2",
    "com.typesafe"                  %  "config"                         % "1.3.3",
    "ch.qos.logback"                %  "logback-classic"                % "1.2.3",
    "ch.qos.logback.contrib"        %  "logback-json-classic"            % "0.1.5",
    "ch.qos.logback.contrib"        %  "logback-jackson"                 % "0.1.5",
    "com.googlecode.libphonenumber" %  "libphonenumber"                 % "8.9.7"
  ))
  .settings(scalaVersion := "2.12.6")
