resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases"
resolvers += "snapshots" at "https://drivergrp.jfrog.io/drivergrp/snapshots"
credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "ANC-d8X-Whm-USS")

addSbtPlugin("com.drivergrp" % "sbt-settings" % "0.4.3-SNAPSHOT")
