package xyz.driver.core
package logging

import java.text.SimpleDateFormat
import java.util.Date

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import org.apache.commons.lang3.StringUtils

class DriverLayout extends LayoutBase[ILoggingEvent] {
  import scala.collection.JavaConverters._

  private val FieldSeparator        = "="
  private val DateFormatString      = "yyyy-MM-dd HH:mm:ss.SSS"
  private val newline               = System.getProperty("line.separator")
  private val IgnoredClassesInStack = Set("org.apache.catalina", "org.apache.coyote", "sun.reflect", "javax.servlet")

  override def doLayout(loggingEvent: ILoggingEvent): String = {

    val date  = new SimpleDateFormat(DateFormatString).format(new Date(loggingEvent.getTimeStamp))
    val level = StringUtils.rightPad(loggingEvent.getLevel.toString, 5)

    val message = new StringBuilder(s"$date [$level] - ${loggingEvent.getMessage}$newline")

    logContext(message, loggingEvent)

    Option(loggingEvent.getCallerData) foreach { stacktrace =>
      val stacktraceLength = stacktrace.length

      if (stacktraceLength > 0) {
        val location = stacktrace.head

        val _ = message
          .append(s"Location: ${location.getClassName}.${location.getMethodName}:${location.getLineNumber}$newline")
          .append(s"Exception: ${location.toString}$newline")

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
