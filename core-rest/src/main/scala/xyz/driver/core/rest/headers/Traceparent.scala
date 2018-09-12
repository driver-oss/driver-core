package xyz.driver.core
package rest
package headers

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}
import xyz.driver.core.reporting.SpanContext

import scala.util.Try

/** Encapsulates a trace context in an HTTP header for propagation across services.
  *
  * This implementation corresponds to the W3C editor's draft specification (as of 2018-08-28)
  * https://w3c.github.io/distributed-tracing/report-trace-context.html. The 'flags' field is
  * ignored.
  */
final case class Traceparent(spanContext: SpanContext) extends ModeledCustomHeader[Traceparent] {
  override def renderInRequests            = true
  override def renderInResponses           = true
  override val companion: Traceparent.type = Traceparent
  override def value: String               = f"01-${spanContext.traceId}-${spanContext.spanId}-00"
}
object Traceparent extends ModeledCustomHeaderCompanion[Traceparent] {
  override val name = "traceparent"
  override def parse(value: String) = Try {
    val Array(version, traceId, spanId, _) = value.split("-")
    require(
      version == "01",
      s"Found unsupported version '$version' in traceparent header. Only version '01' is supported.")
    new Traceparent(
      new SpanContext(traceId, spanId)
    )
  }
}
