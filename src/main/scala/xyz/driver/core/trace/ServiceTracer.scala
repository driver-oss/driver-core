package xyz.driver.core.trace

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader

trait SpanWithHeader {
  def header: RawHeader
}

trait ServiceTracer[T <: SpanWithHeader] {
  def startSpan(httpRequest: HttpRequest): T

  def endSpan(span: T): Unit

  val headerKey: String
}
