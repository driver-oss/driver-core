package xyz.driver.core.reporting
import com.typesafe.scalalogging.Logger

trait ScalaLoggerLike extends Reporter {

  def logger: Logger

  override def log(severity: Reporter.Severity, message: String, reason: Option[Throwable])(
      implicit ctx: SpanContext): Unit = severity match {
    case Reporter.Severity.Debug         => logger.debug(message, reason.orNull)
    case Reporter.Severity.Informational => logger.info(message, reason.orNull)
    case Reporter.Severity.Warning       => logger.warn(message, reason.orNull)
    case Reporter.Severity.Error         => logger.error(message, reason.orNull)
  }

}

object ScalaLoggerLike {
  import scala.language.implicitConversions

  def defaultScalaLogger(json: Boolean = false): Logger = {
    if (json) {
      System.setProperty("logback.configurationFile", "deployed-logback.xml")
    } else {
      System.setProperty("logback.configurationFile", "logback.xml")
    }
    Logger.apply("application")
  }

  implicit def toScalaLogger(reporter: ScalaLoggerLike): Logger = reporter.logger

}
