package xyz.driver.core.trace

import akka.http.scaladsl.model.HttpRequest
import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.typesafe.scalalogging.Logger

final class LoggingTrace(appName: String, appEnvironment: String, log: Logger, bufferSize: Int)
    extends GoogleServiceTracer {

  private val traceConsumer: TraceConsumer = new LoggingTraceConsumer(log)
  private val googleServiceTracer = new GoogleStackdriverTraceWithConsumer(
    "logging-tracer",
    appName,
    appEnvironment,
    traceConsumer,
    log,
    bufferSize
  )

  override def startSpan(httpRequest: HttpRequest): GoogleStackdriverTraceSpan =
    googleServiceTracer.startSpan(httpRequest)

  override def endSpan(span: GoogleStackdriverTraceSpan): Unit = googleServiceTracer.endSpan(span)

  override def flush(): Unit = googleServiceTracer.flush()
}
