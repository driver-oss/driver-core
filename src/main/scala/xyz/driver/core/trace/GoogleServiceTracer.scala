package xyz.driver.core.trace

import akka.http.scaladsl.model.headers.RawHeader
import com.google.cloud.trace.Tracer
import com.google.cloud.trace.core.{SpanContextFactory, TraceContext}

final case class GoogleStackdriverTraceSpan(tracer: Tracer, context: TraceContext) extends CanMakeHeader {
  def header: RawHeader =
    RawHeader(TracingHeaderKey, SpanContextFactory.toHeader(context.getHandle.getCurrentSpanContext))
}

trait GoogleServiceTracer extends ServiceTracer {
  type TracerSpanPayload = GoogleStackdriverTraceSpan
}
