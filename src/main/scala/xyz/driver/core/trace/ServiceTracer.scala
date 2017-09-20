package xyz.driver.core.trace

import java.util.UUID

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader

trait DriverTracer {
  def startSpan(appName: String, httpRequest: HttpRequest): (UUID, RawHeader)

  def endSpan(uuid: UUID): Unit

  val headerKey: String
}
