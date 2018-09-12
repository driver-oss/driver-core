import sbt._
import Keys._

val testdeps = libraryDependencies ++= Seq(
  "org.mockito"                   % "mockito-core"           % "1.9.5" % "test",
  "org.scalacheck"                %% "scalacheck"            % "1.14.0" % "test",
  "org.scalatest"                 %% "scalatest"             % "3.0.5" % "test",
)
lazy val `core-util` = project
  .enablePlugins(LibraryPlugin)
  .settings(
    libraryDependencies ++= Seq(
      // please keep these sorted alphabetically
      "ch.qos.logback"                % "logback-classic"        % "1.2.3",
      "ch.qos.logback.contrib"        % "logback-jackson"        % "0.1.5",
      "ch.qos.logback.contrib"        % "logback-json-classic"   % "0.1.5",
      "com.aliyun.mns"                % "aliyun-sdk-mns"         % "1.1.8",
      "com.aliyun.oss"                % "aliyun-sdk-oss"         % "2.8.2",
      "com.amazonaws"                 % "aws-java-sdk-s3"        % "1.11.342",
      "com.beachape"                  %% "enumeratum"            % "1.5.13",
      "com.github.swagger-akka-http"  %% "swagger-akka-http"     % "1.0.0",
      "com.google.cloud"              % "google-cloud-pubsub"    % "1.31.0",
      "com.google.cloud"              % "google-cloud-storage"   % "1.31.0",
      "com.googlecode.libphonenumber" % "libphonenumber"         % "8.9.7",
      "com.neovisionaries"            % "nv-i18n"                % "1.23",
      "com.pauldijou"                 %% "jwt-core"              % "0.16.0",
      "com.softwaremill.sttp"         %% "akka-http-backend"     % "1.2.2",
      "com.softwaremill.sttp"         %% "core"                  % "1.2.2",
      "com.typesafe"                  % "config"                 % "1.3.3",
      "com.typesafe.akka"             %% "akka-actor"            % "2.5.14",
      "com.typesafe.akka"             %% "akka-http-core"        % "10.1.4",
      "com.typesafe.akka"             %% "akka-http-spray-json"  % "10.1.4",
      "com.typesafe.akka"             %% "akka-http-testkit"     % "10.1.4",
      "com.typesafe.akka"             %% "akka-stream"           % "2.5.14",
      "com.typesafe.scala-logging"    %% "scala-logging"         % "3.9.0",
      "com.typesafe.slick"            %% "slick"                 % "3.2.3",
      "eu.timepit"                    %% "refined"               % "0.9.0",
      "io.kamon"                      %% "kamon-akka-2.5"        % "1.0.0",
      "io.kamon"                      %% "kamon-core"            % "1.1.3",
      "io.kamon"                      %% "kamon-statsd"          % "1.0.0",
      "io.kamon"                      %% "kamon-system-metrics"  % "1.0.0",
      "javax.xml.bind"                % "jaxb-api"               % "2.2.8",
      "org.scala-lang.modules"        %% "scala-async"           % "0.9.7",
      "org.scalaz"                    %% "scalaz-core"           % "7.2.24",
      "xyz.driver"                    %% "spray-json-derivation" % "0.6.0",
      "xyz.driver"                    %% "tracing"               % "0.1.2"
    )
  )

lazy val `core-types` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-util`)
  .settings(testdeps)

lazy val `core-rest` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-util`, `core-types`)
  .settings(testdeps)

lazy val `core-reporting` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-util`)
  .settings(testdeps)

lazy val `core-storage` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-reporting`)
  .settings(testdeps)

lazy val `core-messaging` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-reporting`)
  .settings(testdeps)

lazy val `core-init` = project
  .enablePlugins(LibraryPlugin)
  .dependsOn(`core-reporting`, `core-storage`, `core-messaging`, `core-rest`)
  .settings(testdeps)

lazy val core = project
  .in(file("."))
  .enablePlugins(LibraryPlugin)
  .settings(
    scalacOptions in (Compile, doc) ++= Seq(
      "-groups", // group similar methods together based on the @group annotation.
      "-diagrams", // show classs hierarchy diagrams (requires 'dot' to be available on path)
      "-implicits", // add methods "inherited" through implicit conversions
      "-sourcepath",
      baseDirectory.value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/drivergroup/driver-core/blob/master€{FILE_PATH}.scala"
    )
  )
  .dependsOn(`core-types`, `core-rest`, `core-reporting`, `core-storage`, `core-messaging`, `core-init`)
  .settings(testdeps)
