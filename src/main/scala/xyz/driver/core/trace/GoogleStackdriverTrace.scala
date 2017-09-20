package xyz.driver.core.trace

import java.io.FileInputStream
import java.util
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.trace.core._
import com.google.cloud.trace.grpc.v1.GrpcTraceConsumer
import com.google.cloud.trace.sink.TraceSink
import com.google.cloud.trace.v1.TraceSinkV1
import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.google.cloud.trace.v1.producer.TraceProducer
import com.google.cloud.trace.{SpanContextHandler, SpanContextHandlerTracer, Tracer}
import com.typesafe.scalalogging.Logger

import scala.collection.mutable

@SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
final class GoogleStackdriverTrace(projectId: String, clientSecretsFile: String, log: Logger)(
        implicit system: ActorSystem)
    extends DriverTracer {
  import GoogleStackdriverTrace._
  // initialize our various tracking storage systems
  private val contextMap: mutable.Map[UUID, (Tracer, TraceContext)] =
    mutable.Map.empty[UUID, (Tracer, TraceContext)]
  val clientSecretsInputStreamOpt: Option[FileInputStream] = if (fileExists(clientSecretsFile)) {
    Some(new FileInputStream(clientSecretsFile))
  } else {
    None
  }
  private val traceProducer: TraceProducer = new TraceProducer()
  // if the google credentials are invalid, just log the traces
  private val traceConsumer: TraceConsumer = clientSecretsInputStreamOpt.fold[TraceConsumer] {
    log.debug(s"Google credentials not found in path: $clientSecretsFile")
    new LoggingTraceConsumer(log)
  } { clientSecretsInputStream =>
    GrpcTraceConsumer
      .create(
        "cloudtrace.googleapis.com",
        GoogleCredentials
          .fromStream(clientSecretsInputStream)
          .createScoped(util.Arrays.asList("https://www.googleapis.com/auth/trace.append"))
      )
  }

  private val traceSink: TraceSink = new TraceSinkV1(projectId, traceProducer, traceConsumer)

  private val spanContextFactory: SpanContextFactory = new SpanContextFactory(
    new ConstantTraceOptionsFactory(true, true))
  private val timestampFactory: TimestampFactory = new JavaTimestampFactory()

  override val HeaderKey: String = {
    SpanContextFactory.headerKey()
  }

  override def startSpan(appName: String, httpRequest: HttpRequest): (UUID, RawHeader) = {
    val uuid                 = UUID.randomUUID()
    val parentHeaderOptional = httpRequest.getHeader(HeaderKey)
    val (spanContext: SpanContext, spanKind: SpanKind) = if (parentHeaderOptional.isPresent) {
      (spanContextFactory.fromHeader(parentHeaderOptional.get().value()), SpanKind.RPC_SERVER)
    } else {
      (spanContextFactory.initialContext(), SpanKind.RPC_CLIENT)
    }

    val contextHandler: SpanContextHandler = new SimpleSpanContextHandler(spanContext)
    val httpMethod                         = httpRequest.method.value
    val httpHost                           = httpRequest.uri.authority.host.address()
    val httpRelative                       = httpRequest.uri.toRelative.toString()
    val tracer: Tracer                     = new SpanContextHandlerTracer(traceSink, contextHandler, spanContextFactory, timestampFactory)
    // Create a span using the given timestamps.
    // https://cloud.google.com/trace/docs/reference/v1/rest/v1/projects.traces#TraceSpan
    val spanOptions: StartSpanOptions = (new StartSpanOptions()).setSpanKind(spanKind)
    synchronized {
      val context: TraceContext = tracer.startSpan(s"($appName)$httpRelative", spanOptions)
      val spanLabelBuilder = Labels
        .builder()
        .add("/http/method", httpMethod)
        .add("/http/url", httpRelative)
        .add("/http/host", httpHost)
        .add("/component", appName)

      if (parentHeaderOptional.isPresent) {
        spanLabelBuilder.add("/span/parent", parentHeaderOptional.get().value())
      }

      tracer.annotateSpan(context, spanLabelBuilder.build())

      contextMap.put(uuid, (tracer, context))
      (uuid, RawHeader(HeaderKey, SpanContextFactory.toHeader(context.getHandle.getCurrentSpanContext)))
    }
  }

  override def endSpan(uuid: UUID): Unit =
    contextMap.get(uuid) match {
      case Some((tracer, context)) =>
        synchronized {
          tracer.endSpan(context)
          contextMap.remove(uuid)
        }
      case None => log.error("ERROR you are asking to stop a span that was not found in tracing.")
    }
}

object GoogleStackdriverTrace {
  import java.nio.file.{Paths, Files}
  def fileExists(path: String): Boolean = Files.exists(Paths.get(path))
}
