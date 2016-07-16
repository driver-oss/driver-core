package com.drivergrp.core

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}


object config {

  def loadDefaultConfig: Config = {
    val configDefaults =
      ConfigFactory.load(this.getClass.getClassLoader, "application.conf")

    scala.sys.props.get("application.config") match {

      case Some(filename) =>
        ConfigFactory.parseFile(new File(filename)).withFallback(configDefaults)

      case None => configDefaults
    }
  }
}
