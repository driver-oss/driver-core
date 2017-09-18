package xyz.driver.core

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace.GrpcSpanContextHandler
import com.google.cloud.trace.SpanContextHandler
import com.google.cloud.trace.SpanContextHandlerTracer
import com.google.cloud.trace.Tracer
import com.google.cloud.trace.core._
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer
import com.google.cloud.trace.sink.TraceSink
import com.google.cloud.trace.v1.TraceSinkV1
import com.google.cloud.trace.v1.consumer.FlushableTraceConsumer
import com.google.cloud.trace.v1.consumer.SimpleBufferingTraceConsumer
import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.google.cloud.trace.v1.producer.TraceProducer
import java.io.FileInputStream
import java.util

final case class GoogleStackdriverTrace(appName: String, projectId: String, clientSecretsFile:String, parentTraceHeaderStringOpt: Option[String]) {

  // Create the trace sink.
  val traceProducer: TraceProducer = new TraceProducer
  val traceConsumer: TraceConsumer = GrpcTraceConsumer
    .create("cloudtrace.googleapis.com",
      GoogleCredentials.fromStream(new FileInputStream(clientSecretsFile))
        .createScoped(util.Arrays.asList("https://www.googleapis.com/auth/trace.append")))
  val traceSink: TraceSink = new TraceSinkV1(projectId, traceProducer, traceConsumer)

  // Create the tracer.
  val spanContextFactory: SpanContextFactory = new SpanContextFactory(new ConstantTraceOptionsFactory(true, true))
  val timestampFactory: TimestampFactory = new JavaTimestampFactory
  val spanContext: SpanContext = parentTraceHeaderStringOpt
    .fold(spanContextFactory.initialContext())(
      parentTraceHeaderString => spanContextFactory.childContext(spanContextFactory.fromHeader(parentTraceHeaderString))
    )
  val contextHandler: SpanContextHandler = new GrpcSpanContextHandler(spanContext)
  val tracer: Tracer = new SpanContextHandlerTracer(traceSink, contextHandler, spanContextFactory, timestampFactory)

  // Create a span using the given timestamps.
  val context: TraceContext = tracer.startSpan(appName)

  val stackTraceBuilder: StackTrace.Builder = ThrowableStackTraceHelper.createBuilder(new Exception)
  tracer.setStackTrace(context, stackTraceBuilder.build)

  def endSpan(): Unit = {
    tracer.endSpan(context)
  }
  def headerValue: String = {
    SpanContextFactory.toHeader(spanContext)
  }
}

object GoogleStackdriverTrace{
  val HeaderKey: String = {
    SpanContextFactory.headerKey()
  }
}
