package xyz.driver.core

import java.io.FileInputStream
import java.util

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace._
import com.google.cloud.trace.core._
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer
import com.google.cloud.trace.sink.TraceSink
import com.google.cloud.trace.v1.TraceSinkV1
import com.google.cloud.trace.v1.consumer.{SimpleBufferingTraceConsumer, TraceConsumer}
import com.google.cloud.trace.v1.producer.TraceProducer


trait DriverTracing {
  def endSpan():Unit
  def headerValue: String
}

final class GoogleStackdriverTrace(appName: String,
                                   projectId: String,
                                   clientSecretsFile:String,
                                   parentTraceHeaderStringOpt: Option[String]) extends DriverTracing {

  // Create the trace sink.
  val traceProducer: TraceProducer = new TraceProducer()
  val traceConsumer: TraceConsumer = GrpcTraceConsumer
    .create("cloudtrace.googleapis.com",
      GoogleCredentials.fromStream(new FileInputStream(clientSecretsFile))
        .createScoped(util.Arrays.asList("https://www.googleapis.com/auth/trace.append")))

  val flushableSink = new SimpleBufferingTraceConsumer(traceConsumer)
  val traceSink: TraceSink = new TraceSinkV1(projectId, traceProducer, flushableSink)

  // Create the tracer.
  val spanContextFactory: SpanContextFactory = new SpanContextFactory(new ConstantTraceOptionsFactory(true, true))
  val timestampFactory: TimestampFactory = new JavaTimestampFactory()
  val spanContext: SpanContext = parentTraceHeaderStringOpt
    .fold(spanContextFactory.initialContext())(
      parentTraceHeaderString => spanContextFactory.fromHeader(parentTraceHeaderString)
    )
  val contextHandler: SpanContextHandler = new GrpcSpanContextHandler(spanContext)

  val tracer: Tracer = new SpanContextHandlerTracer(traceSink, contextHandler, spanContextFactory, timestampFactory)

  // Create a span using the given timestamps.
  val context: TraceContext = tracer.startSpan(appName)

  def endSpan(): Unit = {
    tracer.endSpan(context)
    flushableSink.flush()
  }
  def headerValue: String = {
    SpanContextFactory.toHeader(context.getHandle.getCurrentSpanContext)
  }
}

object GoogleStackdriverTrace{
  val HeaderKey: String = {
    SpanContextFactory.headerKey()
  }
}
