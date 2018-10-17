package xyz.driver.core
package reporting

import java.security.Signature
import java.time.Instant
import java.util

import akka.NotUsed
import akka.stream.scaladsl.{Flow, RestartSink, Sink, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import com.google.auth.oauth2.ServiceAccountCredentials
import com.softwaremill.sttp._
import spray.json.DerivedJsonProtocol._
import spray.json._
import xyz.driver.core.reporting.Reporter.CausalRelation

import scala.async.Async._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Random, Success, Try}

/** A reporter that collects traces and submits them to
  * [[https://cloud.google.com/trace/docs/reference/v2/rest/ Google's Stackdriver Trace API]].
  */
class GoogleReporter(
    val credentials: ServiceAccountCredentials,
    namespace: String,
    buffer: Int = GoogleReporter.DefaultBufferSize,
    interval: FiniteDuration = GoogleReporter.DefaultInterval)(
    implicit client: SttpBackend[Future, _],
    mat: Materializer,
    ec: ExecutionContext
) extends Reporter {
  import GoogleReporter._

  private val getToken: () => Future[String] = Refresh.every(55.minutes) {
    def jwt = {
      val now    = Instant.now().getEpochSecond
      val base64 = util.Base64.getEncoder
      val header = base64.encodeToString("""{"alg":"RS256","typ":"JWT"}""".getBytes("utf-8"))
      val body = base64.encodeToString(
        s"""|{
            | "iss": "${credentials.getClientEmail}",
            | "scope": "https://www.googleapis.com/auth/trace.append",
            | "aud": "https://www.googleapis.com/oauth2/v4/token",
            | "exp": ${now + 60.minutes.toSeconds},
            | "iat": $now
            |}""".stripMargin.getBytes("utf-8")
      )
      val signer = Signature.getInstance("SHA256withRSA")
      signer.initSign(credentials.getPrivateKey)
      signer.update(s"$header.$body".getBytes("utf-8"))
      val signature = base64.encodeToString(signer.sign())
      s"$header.$body.$signature"
    }
    sttp
      .post(uri"https://www.googleapis.com/oauth2/v4/token")
      .body(
        "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion"  -> jwt
      )
      .mapResponse(s => s.parseJson.asJsObject.fields("access_token").convertTo[String])
      .send()
      .map(_.unsafeBody)
  }

  private val sendToGoogle: Sink[Span, NotUsed] = RestartSink.withBackoff(
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
  ) { () =>
    Flow[Span]
      .groupedWithin(buffer, interval)
      .mapAsync(1) { spans =>
        async {
          val token = await(getToken())
          val res = await(
            sttp
              .post(uri"https://cloudtrace.googleapis.com/v2/projects/${credentials.getProjectId}/traces:batchWrite")
              .auth
              .bearer(token)
              .body(
                Spans(spans).toJson.compactPrint
              )
              .send()
              .map(_.unsafeBody))
          res
        }
      }
      .recover {
        case NonFatal(e) =>
          System.err.println(s"Error submitting trace spans: $e") // scalastyle: ignore
          throw e
      }
      .to(Sink.ignore)
  }
  private val queue: SourceQueueWithComplete[Span] = Source
    .queue[Span](buffer, OverflowStrategy.dropHead)
    .to(sendToGoogle)
    .run()

  private def submit(span: Span): Unit = queue.offer(span).failed.map { e =>
    System.err.println(s"Error adding span to submission queue: $e")
  }

  private def startSpan(
      traceId: String,
      spanId: String,
      parentSpanId: Option[String],
      displayName: String,
      attributes: Map[String, String]) = Span(
    s"projects/${credentials.getProjectId}/traces/$traceId/spans/$spanId",
    spanId,
    parentSpanId,
    TruncatableString(displayName),
    Instant.now(),
    Instant.now(),
    Attributes(attributes ++ Map("service.namespace" -> namespace)),
    Failure(new IllegalStateException("span status not set"))
  )

  def traceWithOptionalParent[A](
      operationName: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, CausalRelation)])(operation: SpanContext => A): A = {
    val child = parent match {
      case Some((p, _)) => SpanContext(p.traceId, f"${Random.nextLong()}%016x")
      case None         => SpanContext.fresh()
    }
    val span   = startSpan(child.traceId, child.spanId, parent.map(_._1.spanId), operationName, tags)
    val result = operation(child)
    span.endTime = Instant.now()
    submit(span)
    result
  }

  def traceWithOptionalParentAsync[A](
      operationName: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, CausalRelation)])(operation: SpanContext => Future[A]): Future[A] = {
    val child = parent match {
      case Some((p, _)) => SpanContext(p.traceId, f"${Random.nextLong()}%016x")
      case None         => SpanContext.fresh()
    }
    val span   = startSpan(child.traceId, child.spanId, parent.map(_._1.spanId), operationName, tags)
    val result = operation(child)
    result.onComplete { result =>
      span.endTime = Instant.now()
      span.status = result
      submit(span)
    }
    result
  }

  override def log(severity: Reporter.Severity, message: String, reason: Option[Throwable])(
      implicit ctx: SpanContext): Unit =
    sys.error("Submitting logs directly to GCP is " +
      "currently not supported. Messages should go to stdout.") // TODO: attach logs to traces and submit them directly

}

object GoogleReporter {

  val DefaultBufferSize: Int          = 10000
  val DefaultInterval: FiniteDuration = 5.seconds

  private case class Attributes(attributeMap: Map[String, String])
  private case class TruncatableString(value: String)
  private case class Span(
      name: String,
      spanId: String,
      parentSpanId: Option[String],
      displayName: TruncatableString,
      startTime: Instant,
      var endTime: Instant,
      var attributes: Attributes,
      var status: Try[_]
  )

  private case class Spans(spans: Seq[Span])

  private implicit val instantFormat: RootJsonFormat[Instant] = new RootJsonFormat[Instant] {
    override def write(obj: Instant): JsValue = obj.toString.toJson
    override def read(json: JsValue): Instant = Instant.parse(json.convertTo[String])
  }

  private implicit val mapFormat = new RootJsonFormat[Map[String, String]] {
    override def read(json: JsValue): Map[String, String] = sys.error("unimplemented")
    override def write(obj: Map[String, String]): JsValue = {
      val withValueObjects = obj.mapValues(value => JsObject("stringValue" -> JsObject("value" -> value.toJson)))
      JsObject(withValueObjects)
    }
  }

  private implicit val statusFormat = new RootJsonFormat[Try[_]] {
    override def read(json: JsValue): Try[_] = sys.error("unimplemented")
    override def write(obj: Try[_]) = {
      // error codes defined at https://github.com/googleapis/googleapis/blob/master/google/rpc/code.proto
      val (code, message) = obj match {
        case Success(_) => (0, "success")
        case Failure(_) => (2, "failure")
      }
      JsObject(
        "code"    -> code.toJson,
        "message" -> message.toJson,
        "details" -> JsArray()
      )
    }
  }

  private implicit val attributeFormat: RootJsonFormat[Attributes]                = jsonFormat1(Attributes)
  private implicit val truncatableStringFormat: RootJsonFormat[TruncatableString] = jsonFormat1(TruncatableString)
  private implicit val spanFormat: RootJsonFormat[Span]                           = jsonFormat8(Span)
  private implicit val spansFormat: RootJsonFormat[Spans]                         = jsonFormat1(Spans)

}
