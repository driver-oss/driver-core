lazy val `example-app` = project
  .in(file("."))
  .enablePlugins(ServicePlugin)
  .settings(
    libraryDependencies += "xyz.driver" %% "core-init" % "2.0.0-M5"
  )
