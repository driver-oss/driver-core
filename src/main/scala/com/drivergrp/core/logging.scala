package com.drivergrp.core

import org.slf4j.Marker

object logging {

  trait Logger {

    def fatal(message: String): Unit
    def fatal(message: String, cause: Throwable): Unit
    def fatal(message: String, args: AnyRef*): Unit
    def fatal(marker: Marker, message: String): Unit
    def fatal(marker: Marker, message: String, cause: Throwable): Unit
    def fatal(marker: Marker, message: String, args: AnyRef*): Unit

    def error(message: String): Unit
    def error(message: String, cause: Throwable): Unit
    def error(message: String, args: AnyRef*): Unit
    def error(marker: Marker, message: String): Unit
    def error(marker: Marker, message: String, cause: Throwable): Unit
    def error(marker: Marker, message: String, args: AnyRef*): Unit

    def audit(message: String): Unit
    def audit(message: String, cause: Throwable): Unit
    def audit(message: String, args: AnyRef*): Unit
    def audit(marker: Marker, message: String): Unit
    def audit(marker: Marker, message: String, cause: Throwable): Unit
    def audit(marker: Marker, message: String, args: AnyRef*): Unit

    def debug(message: String): Unit
    def debug(message: String, cause: Throwable): Unit
    def debug(message: String, args: AnyRef*): Unit
    def debug(marker: Marker, message: String): Unit
    def debug(marker: Marker, message: String, cause: Throwable): Unit
    def debug(marker: Marker, message: String, args: AnyRef*): Unit
  }

  /**
    * Logger implementation which uses `com.typesafe.scalalogging.Logger` on the back.
    * It redefines the meaning of logging levels to fit to the Driver infrastructure design,
    * and as using error and warn, debug and trace was always confusing and mostly done wrong.
    *
    * @param scalaLogging com.typesafe.scalalogging.Logger which logging will be delegated to
    */
  class TypesafeScalaLogger(scalaLogging: com.typesafe.scalalogging.Logger) extends Logger {

    def fatal(message: String): Unit                                   = scalaLogging.error(message)
    def fatal(message: String, cause: Throwable): Unit                 = scalaLogging.error(message, cause)
    def fatal(message: String, args: AnyRef*): Unit                    = scalaLogging.error(message, args)
    def fatal(marker: Marker, message: String): Unit                   = scalaLogging.error(marker, message)
    def fatal(marker: Marker, message: String, cause: Throwable): Unit = scalaLogging.error(marker, message, cause)
    def fatal(marker: Marker, message: String, args: AnyRef*): Unit    = scalaLogging.error(marker, message, args)

    def error(message: String): Unit                                   = scalaLogging.warn(message)
    def error(message: String, cause: Throwable): Unit                 = scalaLogging.warn(message, cause)
    def error(message: String, args: AnyRef*): Unit                    = scalaLogging.warn(message, args)
    def error(marker: Marker, message: String): Unit                   = scalaLogging.warn(marker, message)
    def error(marker: Marker, message: String, cause: Throwable): Unit = scalaLogging.warn(marker, message, cause)
    def error(marker: Marker, message: String, args: AnyRef*): Unit    = scalaLogging.warn(marker, message, args)

    def audit(message: String): Unit                                   = scalaLogging.info(message)
    def audit(message: String, cause: Throwable): Unit                 = scalaLogging.info(message, cause)
    def audit(message: String, args: AnyRef*): Unit                    = scalaLogging.info(message, args)
    def audit(marker: Marker, message: String): Unit                   = scalaLogging.info(marker, message)
    def audit(marker: Marker, message: String, cause: Throwable): Unit = scalaLogging.info(marker, message, cause)
    def audit(marker: Marker, message: String, args: AnyRef*): Unit    = scalaLogging.info(marker, message, args)

    def debug(message: String): Unit                                   = scalaLogging.debug(message)
    def debug(message: String, cause: Throwable): Unit                 = scalaLogging.debug(message, cause)
    def debug(message: String, args: AnyRef*): Unit                    = scalaLogging.debug(message, args)
    def debug(marker: Marker, message: String): Unit                   = scalaLogging.debug(marker, message)
    def debug(marker: Marker, message: String, cause: Throwable): Unit = scalaLogging.debug(marker, message, cause)
    def debug(marker: Marker, message: String, args: AnyRef*): Unit    = scalaLogging.debug(marker, message, args)
  }

  class NoLogger() extends Logger {

    def fatal(message: String): Unit                                   = {}
    def fatal(message: String, cause: Throwable): Unit                 = {}
    def fatal(message: String, args: AnyRef*): Unit                    = {}
    def fatal(marker: Marker, message: String): Unit                   = {}
    def fatal(marker: Marker, message: String, cause: Throwable): Unit = {}
    def fatal(marker: Marker, message: String, args: AnyRef*): Unit    = {}

    def error(message: String): Unit                                   = {}
    def error(message: String, cause: Throwable): Unit                 = {}
    def error(message: String, args: AnyRef*): Unit                    = {}
    def error(marker: Marker, message: String): Unit                   = {}
    def error(marker: Marker, message: String, cause: Throwable): Unit = {}
    def error(marker: Marker, message: String, args: AnyRef*): Unit    = {}

    def audit(message: String): Unit                                   = {}
    def audit(message: String, cause: Throwable): Unit                 = {}
    def audit(message: String, args: AnyRef*): Unit                    = {}
    def audit(marker: Marker, message: String): Unit                   = {}
    def audit(marker: Marker, message: String, cause: Throwable): Unit = {}
    def audit(marker: Marker, message: String, args: AnyRef*): Unit    = {}

    def debug(message: String): Unit                                   = {}
    def debug(message: String, cause: Throwable): Unit                 = {}
    def debug(message: String, args: AnyRef*): Unit                    = {}
    def debug(marker: Marker, message: String): Unit                   = {}
    def debug(marker: Marker, message: String, cause: Throwable): Unit = {}
    def debug(marker: Marker, message: String, args: AnyRef*): Unit    = {}
  }
}
