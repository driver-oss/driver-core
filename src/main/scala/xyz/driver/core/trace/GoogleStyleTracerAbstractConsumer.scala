package xyz.driver.core.trace

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.google.cloud.trace.core._
import com.google.cloud.trace.sink.TraceSink
import com.google.cloud.trace.v1.TraceSinkV1
import com.google.cloud.trace.v1.consumer.{SizedBufferingTraceConsumer, TraceConsumer}
import com.google.cloud.trace.v1.producer.TraceProducer
import com.google.cloud.trace.v1.util.RoughTraceSizer
import com.google.cloud.trace.{SpanContextHandler, SpanContextHandlerTracer, Tracer}

import scala.compat.java8.OptionConverters._

final case class GoogleStackdriverTraceSpan(tracer: Tracer, context: TraceContext) extends CanMakeHeader {
  def header: RawHeader =
    RawHeader(TracingHeaderKey, SpanContextFactory.toHeader(context.getHandle.getCurrentSpanContext))
}

abstract class GoogleStackdriverTraceAbstractConsumer(projectId: String, appName: String, appEnvironment: String)
    extends ServiceTracer {
  protected val traceConsumer: TraceConsumer
  override type TracerSpanPayload = GoogleStackdriverTraceSpan

  private val traceProducer: TraceProducer = new TraceProducer()
  private val threadSafeBufferingTraceConsumer =
    new SizedBufferingTraceConsumer(traceConsumer, new RoughTraceSizer(), 100)

  private val traceSink: TraceSink = new TraceSinkV1(projectId, traceProducer, threadSafeBufferingTraceConsumer)

  private val spanContextFactory: SpanContextFactory = new SpanContextFactory(
    new ConstantTraceOptionsFactory(true, true))
  private val timestampFactory: TimestampFactory = new JavaTimestampFactory()

  override def startSpan(httpRequest: HttpRequest): TracerSpanPayload = {
    val parentHeaderOption: Option[akka.http.javadsl.model.HttpHeader] =
      httpRequest.getHeader(TracingHeaderKey).asScala
    val (spanContext: SpanContext, spanKind: SpanKind) = parentHeaderOption.fold {
      (spanContextFactory.initialContext(), SpanKind.RPC_CLIENT)
    } { parentHeader =>
      (spanContextFactory.fromHeader(parentHeader.value()), SpanKind.RPC_SERVER)
    }

    val contextHandler: SpanContextHandler = new SimpleSpanContextHandler(spanContext)
    val httpMethod                         = httpRequest.method.value
    val httpHost                           = httpRequest.uri.authority.host.address()
    val httpRelative                       = httpRequest.uri.toRelative.toString()
    val tracer: Tracer                     = new SpanContextHandlerTracer(traceSink, contextHandler, spanContextFactory, timestampFactory)
    // Create a span using the given timestamps.
    // https://cloud.google.com/trace/docs/reference/v1/rest/v1/projects.traces#TraceSpan
    val spanOptions: StartSpanOptions = (new StartSpanOptions()).setSpanKind(spanKind)

    val spanLabelBuilder = Labels
      .builder()
      .add("/http/method", httpMethod)
      .add("/http/url", httpRelative)
      .add("/http/host", httpHost)
      .add("/component", appName)
      .add("/environment", appEnvironment)

    parentHeaderOption.foreach { parentHeader =>
      spanLabelBuilder.add("/span/parent", parentHeader.value())
    }

    // The cloudTrace analysis reporting UI makes it easy to query by name prefix.
    // this spanName gives us the ability to grab things that are specific to a particular UDE/env, as well as all
    // endpoints in that service, as well as a particular endpoint in a particular environment/service.
    val spanName: String = s"($appEnvironment->$appName)$httpRelative"

    val context: TraceContext = tracer.startSpan(spanName, spanOptions)
    tracer.annotateSpan(context, spanLabelBuilder.build())
    GoogleStackdriverTraceSpan(tracer, context)
  }

  override def endSpan(span: TracerSpanPayload): Unit = {
    span.tracer.endSpan(span.context)
    threadSafeBufferingTraceConsumer.flush() // flush out the thread safe buffer
  }

}
