package xyz.driver.core
package reporting

trait NoReporter extends NoTraceReporter {
  override def log(severity: Reporter.Severity, message: String, reason: Option[Throwable])(
      implicit ctx: SpanContext): Unit = ()
}
object NoReporter extends NoReporter
