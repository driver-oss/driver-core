package xyz.driver.core.reporting
import com.typesafe.scalalogging.Logger

trait ScalaLoggerLike extends Reporter {

  def logger: Logger

  override def debug(message: String)(implicit ctx: SpanContext): Unit = logger.debug(message)
  override def info(message: String)(implicit ctx: SpanContext): Unit  = logger.info(message)
  override def warn(message: String)(implicit ctx: SpanContext): Unit  = logger.warn(message)
  override def error(message: String)(implicit ctx: SpanContext): Unit = logger.error(message)
  override def error(message: String, reason: Throwable)(implicit ctx: SpanContext): Unit =
    logger.error(message, reason)

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
