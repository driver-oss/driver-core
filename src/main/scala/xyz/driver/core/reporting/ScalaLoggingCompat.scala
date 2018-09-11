package xyz.driver.core
package reporting

import com.typesafe.scalalogging.{Logger => ScalaLogger}

/** Compatibility mixin for reporters, that enables implicit conversions to scala-logging loggers. */
trait ScalaLoggingCompat extends Reporter {
  import Reporter.Severity

  def logger: ScalaLogger

  override def log(severity: Severity, message: String, reason: Option[Throwable])(implicit ctx: SpanContext): Unit =
    severity match {
      case Severity.Debug         => logger.debug(message, reason.orNull)
      case Severity.Informational => logger.info(message, reason.orNull)
      case Severity.Warning       => logger.warn(message, reason.orNull)
      case Severity.Error         => logger.error(message, reason.orNull)
    }

}

object ScalaLoggingCompat {
  import scala.language.implicitConversions

  def defaultScalaLogger(json: Boolean = false): ScalaLogger = {
    if (json) {
      System.setProperty("logback.configurationFile", "deployed-logback.xml")
    } else {
      System.setProperty("logback.configurationFile", "logback.xml")
    }
    ScalaLogger.apply("application")
  }

  implicit def toScalaLogger(logger: ScalaLoggingCompat): ScalaLogger = logger.logger

}
