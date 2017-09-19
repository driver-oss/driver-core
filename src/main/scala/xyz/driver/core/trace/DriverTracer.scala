package xyz.driver.core.trace

import java.util.UUID

import akka.http.scaladsl.model.headers.RawHeader

trait DriverTracer {
  def startSpan(appName:String, httpMethod: String, uri: String, parentTraceHeaderStringOpt: Option[String]): (UUID, RawHeader)

  def endSpan(uuid: UUID): Unit

  val HeaderKey: String
}