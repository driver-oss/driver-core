import sbt._
import Keys._

lazy val akkaHttpV = "2.4.8"

lazy val core = (project in file("."))
  .settings(name := "core")
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"            %% "akka-http-core"                    % akkaHttpV,
      "com.typesafe.akka"            %% "akka-http-experimental"            % akkaHttpV,
      "com.typesafe.akka"            %% "akka-http-jackson-experimental"    % akkaHttpV,
      "com.typesafe.akka"            %% "akka-http-spray-json-experimental" % akkaHttpV,
      "com.typesafe.akka"            %% "akka-http-testkit"                 % akkaHttpV,
      "org.scalatest"                % "scalatest_2.11"                     % "2.2.6" % "test",
      "org.scalacheck"               %% "scalacheck"                        % "1.12.5" % "test",
      "org.mockito"                  % "mockito-core"                       % "1.9.5" % "test",
      "com.amazonaws"                % "aws-java-sdk-s3"                    % "1.11.26",
      "com.typesafe.slick"           %% "slick"                             % "3.1.1",
      "com.typesafe"                 % "config"                             % "1.2.1",
      "com.typesafe.scala-logging"   %% "scala-logging"                     % "3.1.0",
      "ch.qos.logback"               % "logback-classic"                    % "1.1.3",
      "com.github.swagger-akka-http" %% "swagger-akka-http"                 % "0.7.1"
    ))
  .gitPluginConfiguration
  .settings(lintingSettings ++ formatSettings)
  .settings(repositoriesSettings ++ publicationSettings ++ releaseSettings)
