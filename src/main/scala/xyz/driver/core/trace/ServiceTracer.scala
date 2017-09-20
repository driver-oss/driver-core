package xyz.driver.core.trace

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader

trait ServiceTracer {
  type TraceId

  def startSpan(httpRequest: HttpRequest): (TraceId, RawHeader)

  def endSpan(uuid: TraceId): Unit

  val headerKey: String
}
