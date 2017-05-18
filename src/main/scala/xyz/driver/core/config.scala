package xyz.driver.core

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}

package object config {

  def loadDefaultConfig: Config = {
    val configDefaults = ConfigFactory.load(this.getClass.getClassLoader, "application.conf")

    scala.sys.env.get("APPLICATION_CONFIG").orElse(scala.sys.props.get("application.config")) match {

      case Some(filename) =>
        val configFile = new File(filename)
        if (configFile.exists()) {
          ConfigFactory.parseFile(configFile).withFallback(configDefaults)
        } else {
          throw new IllegalStateException(s"No config found at $filename")
        }

      case None => configDefaults
    }
  }
}
