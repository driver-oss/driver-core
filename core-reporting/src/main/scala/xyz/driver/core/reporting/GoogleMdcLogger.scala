package xyz.driver.core
package reporting

import org.slf4j.MDC

trait GoogleMdcLogger extends Reporter { self: GoogleReporter =>

  abstract override def log(severity: Reporter.Severity, message: String, reason: Option[Throwable])(
      implicit ctx: SpanContext): Unit = {
    MDC.put("trace", s"projects/${credentials.getProjectId}/traces/${ctx.traceId}")
    super.log(severity, message, reason)
  }

}
