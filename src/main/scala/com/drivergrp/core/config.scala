package com.drivergrp.core

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}


object config {

  trait ConfigModule {
    def config: Config
  }

  /**
    * Configuration implementation providing config which is specified as the parameter
    * which might be used for testing purposes
    *
    * @param config fixed config to provide
    */
  class DefaultConfigModule(val config: Config) extends ConfigModule

  /**
    * Configuration implementation reading default typesafe config
    */
  trait TypesafeConfigModule extends ConfigModule {

    private val internalConfig: Config = {
      val configDefaults =
        ConfigFactory.load(this.getClass.getClassLoader, "application.conf")

      scala.sys.props.get("application.config") match {

        case Some(filename) =>
          ConfigFactory.parseFile(new File(filename)).withFallback(configDefaults)

        case None => configDefaults
      }
    }

    protected val rootConfig = internalConfig

    val config = rootConfig
  }
}
