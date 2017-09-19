package xyz.driver.core.trace

import com.google.cloud.trace.SpanContextHandler
import com.google.cloud.trace.DetachedSpanContextHandle
import com.google.cloud.trace.core.{SpanContext, SpanContextHandle}

@SuppressWarnings(
  Array("org.wartremover.warts.Var"))
class SimpleSpanContextHandler(rootSpan: SpanContext) extends SpanContextHandler {
  private var currentSpanContext = rootSpan

  override def current():SpanContext = currentSpanContext

  override def attach(context: SpanContext):SpanContextHandle = {
    currentSpanContext = context
    new DetachedSpanContextHandle(context)
  }
}
