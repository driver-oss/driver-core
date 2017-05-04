resolvers += "releases" at "https://drivergrp.jfrog.io/drivergrp/releases"
credentials += Credentials("Artifactory Realm", "drivergrp.jfrog.io", "sbt-publisher", "ANC-d8X-Whm-USS")

addSbtPlugin("xyz.driver" % "sbt-settings" % "0.7.34")
