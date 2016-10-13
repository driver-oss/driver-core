package com.drivergrp.core

import java.text.SimpleDateFormat
import java.util.Date

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import org.apache.commons.lang3.StringUtils
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

    def fatal(message: String): Unit                   = scalaLogging.error("FATAL " + message)
    def fatal(message: String, cause: Throwable): Unit = scalaLogging.error("FATAL " + message, cause)
    def fatal(message: String, args: AnyRef*): Unit    = scalaLogging.error("FATAL " + message, args)
    def fatal(marker: Marker, message: String): Unit   = scalaLogging.error(marker, "FATAL " + message)
    def fatal(marker: Marker, message: String, cause: Throwable): Unit =
      scalaLogging.error(marker, "FATAL " + message, cause)
    def fatal(marker: Marker, message: String, args: AnyRef*): Unit =
      scalaLogging.error(marker, "FATAL " + message, args)

    def error(message: String): Unit                   = scalaLogging.warn("ERROR " + message)
    def error(message: String, cause: Throwable): Unit = scalaLogging.warn("ERROR " + message, cause)
    def error(message: String, args: AnyRef*): Unit    = scalaLogging.warn("ERROR " + message, args)
    def error(marker: Marker, message: String): Unit   = scalaLogging.warn(marker, "ERROR " + message)
    def error(marker: Marker, message: String, cause: Throwable): Unit =
      scalaLogging.warn(marker, "ERROR " + message, cause)
    def error(marker: Marker, message: String, args: AnyRef*): Unit =
      scalaLogging.warn(marker, "ERROR " + message, args)

    def audit(message: String): Unit                   = scalaLogging.info("AUDIT " + message)
    def audit(message: String, cause: Throwable): Unit = scalaLogging.info("AUDIT " + message, cause)
    def audit(message: String, args: AnyRef*): Unit    = scalaLogging.info("AUDIT " + message, args)
    def audit(marker: Marker, message: String): Unit   = scalaLogging.info(marker, "AUDIT " + message)
    def audit(marker: Marker, message: String, cause: Throwable): Unit =
      scalaLogging.info(marker, "AUDIT " + message, cause)
    def audit(marker: Marker, message: String, args: AnyRef*): Unit =
      scalaLogging.info(marker, "AUDIT " + message, args)

    def debug(message: String): Unit                   = scalaLogging.debug("DEBUG " + message)
    def debug(message: String, cause: Throwable): Unit = scalaLogging.debug("DEBUG " + message, cause)
    def debug(message: String, args: AnyRef*): Unit    = scalaLogging.debug("DEBUG " + message, args)
    def debug(marker: Marker, message: String): Unit   = scalaLogging.debug(marker, "DEBUG " + message)
    def debug(marker: Marker, message: String, cause: Throwable): Unit =
      scalaLogging.debug(marker, "DEBUG " + message, cause)
    def debug(marker: Marker, message: String, args: AnyRef*): Unit =
      scalaLogging.debug(marker, "DEBUG " + message, args)
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

  class DriverLayout extends LayoutBase[ILoggingEvent] {
    import scala.collection.JavaConverters._

    private val AVERAGE_MAXIMAL_MESSAGE_LENGTH = 256
    private val FieldSeparator                 = "="
    private val DateFormatString               = "MM/dd/yyyy HH:mm:ss"
    private val newline                        = System.getProperty("line.separator")
    private val IgnoredClassesInStack          = Set("org.apache.catalina", "org.apache.coyote", "sun.reflect", "javax.servlet")

    override def doLayout(loggingEvent: ILoggingEvent): String = {

      val message = new StringBuilder(AVERAGE_MAXIMAL_MESSAGE_LENGTH)
        .append(new SimpleDateFormat(DateFormatString).format(new Date(loggingEvent.getTimeStamp)))
        .append(" [")
        .append(StringUtils.rightPad(loggingEvent.getLevel.toString, 5))
        .append(']')
        .append(" - ")
        .append(loggingEvent.getMessage)
        .append(newline)

      logContext(message, loggingEvent)

      Option(loggingEvent.getCallerData) foreach { stacktrace =>
        val stacktraceLength = stacktrace.length

        if (stacktraceLength > 0) {
          val location = stacktrace.head

          val _ = message
            .append(s"Location: ${location.getClassName}.${location.getMethodName}:${location.getLineNumber}$newline")
            .append("Exception: ")
            .append(location.toString)
            .append(newline)

          if (stacktraceLength > 1) {
            message.append(stacktrace.tail.filterNot { e =>
              IgnoredClassesInStack.forall(ignored => !e.getClassName.startsWith(ignored))
            } map {
              _.toString
            } mkString newline)
          }
        }
      }

      message.toString
    }

    private def logContext(message: StringBuilder, loggingEvent: ILoggingEvent) = {
      Option(loggingEvent.getMDCPropertyMap).map(_.asScala).filter(_.nonEmpty).foreach { context =>
        message.append(
            context map { case (key, value) => s"$key$FieldSeparator$value" } mkString ("Context: ", " ", newline)
        )
      }
    }
  }
}
