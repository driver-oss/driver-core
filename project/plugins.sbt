resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases"
credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "ANC-d8X-Whm-USS")

addSbtPlugin("com.drivergrp" % "sbt-settings" % "0.5.0")
