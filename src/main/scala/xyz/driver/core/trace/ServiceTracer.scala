package xyz.driver.core.trace

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader

trait CanMakeHeader {
  def header: RawHeader
}

trait ServiceTracer {

  type TracerSpanPayload <: CanMakeHeader

  def startSpan(httpRequest: HttpRequest): TracerSpanPayload

  def endSpan(span: TracerSpanPayload): Unit
}
