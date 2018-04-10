import sbt._
import Keys._

lazy val akkaHttpV = "10.0.11"

lazy val core = (project in file("."))
  .driverLibrary("core")
  .settings(lintingSettings ++ formatSettings)
  .settings(libraryDependencies ++= Seq(
    "xyz.driver"                    %% "tracing"                        % "0.0.2",
    "com.typesafe.akka"             %% "akka-http-core"                 % akkaHttpV,
    "com.typesafe.akka"             %% "akka-http-spray-json"           % akkaHttpV,
    "com.typesafe.akka"             %% "akka-http-testkit"              % akkaHttpV,
    "com.pauldijou"                 %% "jwt-core"                       % "0.14.0",
    "org.scalatest"                 %% "scalatest"                      % "3.0.2" % "test",
    "org.scalacheck"                %% "scalacheck"                     % "1.13.4" % "test",
    "org.scalaz"                    %% "scalaz-core"                    % "7.2.19",
    "com.github.swagger-akka-http"  %% "swagger-akka-http"              % "0.11.2",
    "com.typesafe.scala-logging"    %% "scala-logging"                  % "3.5.0",
    "eu.timepit"                    %% "refined"                        % "0.8.4",
    "com.typesafe.slick"            %% "slick"                          % "3.2.1",
    "com.beachape"                  %% "enumeratum"                     % "1.5.13",
    "org.mockito"                   %  "mockito-core"                   % "1.9.5"       % Test,
    "com.amazonaws"                 %  "aws-java-sdk-s3"                % "1.11.26",
    "com.google.cloud"              %  "google-cloud-pubsub"            % "0.25.0-beta",
    "com.google.cloud"              %  "google-cloud-storage"           % "1.24.1",
    "com.typesafe"                  %  "config"                         % "1.3.1",
    "ch.qos.logback"                %  "logback-classic"                % "1.1.11",
    "com.googlecode.libphonenumber" %  "libphonenumber"                 % "8.9.2"
  ))
