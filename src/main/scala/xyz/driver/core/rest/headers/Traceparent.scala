package xyz.driver.core
package rest
package headers

import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}

import scala.util.Try

/** Encapsulates trace context in an HTTP header for propagation across services.
  *
  * This implementation corresponds to the W3C editor's draft specification (as of 2018-08-28)
  * https://w3c.github.io/distributed-tracing/report-trace-context.html. The 'flags' field is
  * ignored.
  */
final case class Traceparent(traceId: String, spanId: String) extends ModeledCustomHeader[Traceparent] {
  override def renderInRequests            = true
  override def renderInResponses           = true
  override val companion: Traceparent.type = Traceparent
  override def value: String               = f"01-$traceId-$spanId-00"
}
object Traceparent extends ModeledCustomHeaderCompanion[Traceparent] {
  override val name = "traceparent"
  override def parse(value: String) = Try {
    val Array(version, traceId, spanId, _) = value.split("-")
    require(
      version == "01",
      s"Found unsupported version '$version' in traceparent header. Only version '01' is supported.")
    new Traceparent(
      traceId,
      spanId
    )
  }
}
