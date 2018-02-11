package xyz.driver.core.rest

import java.net.InetAddress

import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import xyz.driver.tracing.TracingDirectives

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scalaz.{Functor, OptionT}
import scalaz.Scalaz.{intInstance, stringInstance}
import scalaz.syntax.equal._

trait Service

trait HttpClient {
  def makeRequest(request: HttpRequest): Future[HttpResponse]
}

trait ServiceTransport {

  def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse]

  def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest)(
      implicit mat: Materializer): Future[Unmarshal[ResponseEntity]]
}

object Pagination {

  val Default = Pagination(pageSize = 100, pageNumber = 1)

  def parse(query: Seq[(String, String)]): Try[Pagination] = {
    val IntString = """(\d+)""".r
    def validate(field: String, default: Int) = query.collectFirst { case (`field`, size) => size } match {
      case Some(IntString(x)) if x.toInt > 0 => x.toInt
      case Some(IntString(x))                => throw new Exception(s"$field must greater than zero (found $x)")
      case Some(str)                         => throw new Exception(s"$field must be an integer (found $str)")
      case None                              => default
    }

    Try {
      Pagination(
        validate("pageSize", Pagination.Default.pageSize),
        validate("pageNumber", Pagination.Default.pageNumber))
    }
  }
}

final case class Pagination(pageSize: Int, pageNumber: Int) {
  def offset: Int = pageSize * (pageNumber - 1)
}

final case class ListResponse[+T](items: Seq[T], meta: ListResponse.Meta)

object ListResponse {

  final case class Meta(itemsCount: Int, pageNumber: Int, pageSize: Int)

  object Meta {
    def apply(itemsCount: Int, pagination: Pagination): Meta =
      Meta(itemsCount, pagination.pageNumber, pagination.pageSize)
  }

}

object `package` {

  implicit class OptionTRestAdditions[T](optionT: OptionT[Future, T]) {
    def responseOrNotFound(successCode: StatusCodes.Success = StatusCodes.OK)(
        implicit F: Functor[Future],
        em: ToEntityMarshaller[T]): Future[ToResponseMarshallable] = {
      optionT.fold[ToResponseMarshallable](successCode -> _, StatusCodes.NotFound -> None)
    }
  }

  object ContextHeaders {
    val AuthenticationTokenHeader: String  = "Authorization"
    val PermissionsTokenHeader: String     = "Permissions"
    val AuthenticationHeaderPrefix: String = "Bearer"
    val TrackingIdHeader: String           = "X-Trace"
    val StacktraceHeader: String           = "X-Stacktrace"
    val OriginatingIpHeader: String        = "X-Forwarded-For"
    val ResourceCount: String              = "X-Resource-Count"
    val PageCount: String                  = "X-Page-Count"
    val TraceHeaderName: String            = TracingDirectives.TraceHeaderName
    val SpanHeaderName: String             = TracingDirectives.SpanHeaderName
  }

  object AuthProvider {
    val AuthenticationTokenHeader: String    = ContextHeaders.AuthenticationTokenHeader
    val PermissionsTokenHeader: String       = ContextHeaders.PermissionsTokenHeader
    val SetAuthenticationTokenHeader: String = "set-authorization"
    val SetPermissionsTokenHeader: String    = "set-permissions"
  }

  val AllowedHeaders: Seq[String] =
    Seq(
      "Origin",
      "X-Requested-With",
      "Content-Type",
      "Content-Length",
      "Accept",
      "X-Trace",
      "X-Client-Fingerprint",
      "Access-Control-Allow-Methods",
      "Access-Control-Allow-Origin",
      "Access-Control-Allow-Headers",
      "Server",
      "Date",
      ContextHeaders.TrackingIdHeader,
      ContextHeaders.TraceHeaderName,
      ContextHeaders.SpanHeaderName,
      ContextHeaders.StacktraceHeader,
      ContextHeaders.AuthenticationTokenHeader,
      ContextHeaders.OriginatingIpHeader,
      ContextHeaders.ResourceCount,
      ContextHeaders.PageCount,
      "X-Frame-Options",
      "X-Content-Type-Options",
      "Strict-Transport-Security",
      AuthProvider.SetAuthenticationTokenHeader,
      AuthProvider.SetPermissionsTokenHeader
    )

  def allowOrigin(originHeader: Option[Origin]): `Access-Control-Allow-Origin` =
    `Access-Control-Allow-Origin`(
      originHeader.fold[HttpOriginRange](HttpOriginRange.*)(h => HttpOriginRange(h.origins: _*)))

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def serviceContext: Directive1[ServiceRequestContext] = Directives.serviceContext

  def respondWithCorsAllowedHeaders: Directive0 = {
    respondWithHeaders(
      List[HttpHeader](
        `Access-Control-Allow-Headers`(AllowedHeaders: _*),
        `Access-Control-Expose-Headers`(AllowedHeaders: _*)
      ))
  }

  def respondWithCorsAllowedOriginHeaders(origin: Origin): Directive0 = {
    respondWithHeader {
      `Access-Control-Allow-Origin`(HttpOriginRange(origin.origins: _*))
    }
  }

  def respondWithCorsAllowedMethodHeaders(methods: Set[HttpMethod]): Directive0 = {
    respondWithHeaders(
      List[HttpHeader](
        Allow(methods.to[collection.immutable.Seq]),
        `Access-Control-Allow-Methods`(methods.to[collection.immutable.Seq])
      ))
  }

  @deprecated("Use the directive xyz.driver.core.rest.Directives.serviceContext instead", "driver-core 1.9.0")
  def extractServiceContext(request: HttpRequest, remoteAddress: RemoteAddress): ServiceRequestContext =
    new ServiceRequestContext(
      extractTrackingId(request),
      extractOriginatingIP(request, remoteAddress),
      extractContextHeaders(request))

  @deprecated("Use the directive xyz.driver.core.rest.Directives.trackingId instead", "driver-core 1.9.0")
  def extractTrackingId(request: HttpRequest): String = {
    request.headers
      .find(_.name === ContextHeaders.TrackingIdHeader)
      .fold(java.util.UUID.randomUUID.toString)(_.value())
  }

  @deprecated("Use the directive xyz.driver.core.rest.Directives.optionalOriginatingIP instead", "driver-core 1.9.0")
  def extractOriginatingIP(request: HttpRequest, remoteAddress: RemoteAddress): Option[InetAddress] = {
    request.headers
      .find(_.name === ContextHeaders.OriginatingIpHeader)
      .flatMap(ipName => Try(InetAddress.getByName(ipName.value)).toOption)
      .orElse(remoteAddress.toOption)
  }

  def extractStacktrace(request: HttpRequest): Array[String] =
    request.headers.find(_.name == ContextHeaders.StacktraceHeader).fold("")(_.value()).split("->")

  @deprecated("Use the directive xyz.driver.core.rest.Directives.contextHeaders instead", "driver-core 1.9.0")
  def extractContextHeaders(request: HttpRequest): Map[String, String] = {
    request.headers.filter { h =>
      h.name === ContextHeaders.AuthenticationTokenHeader || h.name === ContextHeaders.TrackingIdHeader ||
      h.name === ContextHeaders.PermissionsTokenHeader || h.name === ContextHeaders.StacktraceHeader ||
      h.name === ContextHeaders.TraceHeaderName || h.name === ContextHeaders.SpanHeaderName ||
      h.name === ContextHeaders.OriginatingIpHeader
    } map { header =>
      if (header.name === ContextHeaders.AuthenticationTokenHeader) {
        header.name -> header.value.stripPrefix(ContextHeaders.AuthenticationHeaderPrefix).trim
      } else {
        header.name -> header.value
      }
    } toMap
  }

  private[rest] def escapeScriptTags(byteString: ByteString): ByteString = {
    @annotation.tailrec
    def dirtyIndices(from: Int, descIndices: List[Int]): List[Int] = {
      val index = byteString.indexOf('/', from)
      if (index === -1) descIndices.reverse
      else {
        val (init, tail) = byteString.splitAt(index)
        if ((init endsWith "<") && (tail startsWith "/sc")) {
          dirtyIndices(index + 1, index :: descIndices)
        } else {
          dirtyIndices(index + 1, descIndices)
        }
      }
    }

    val indices = dirtyIndices(0, Nil)

    indices.headOption.fold(byteString) { head =>
      val builder = ByteString.newBuilder
      builder ++= byteString.take(head)

      (indices :+ byteString.length).sliding(2).foreach {
        case Seq(start, end) =>
          builder += ' '
          builder ++= byteString.slice(start, end)
        case Seq(_) => // Should not match; sliding on at least 2 elements
          assert(indices.nonEmpty, s"Indices should have been nonEmpty: $indices")
      }
      builder.result
    }
  }

  // TODO @stewsquared, is this used anywhere?
  val sanitizeRequestEntity: Directive0 = {
    mapRequest(request => request.mapEntity(entity => entity.transformDataBytes(Flow.fromFunction(escapeScriptTags))))
  }

  val paginated: Directive1[Pagination] = parameterSeq.flatMap { params =>
    Pagination.parse(params) match {
      case Success(pagination) => provide(pagination)
      case Failure(ex) =>
        reject(ValidationRejection("invalid pagination parameter", Some(ex)))
    }
  }

  def paginationQuery(pagination: Pagination) =
    Seq("pageNumber" -> pagination.pageNumber.toString, "pageSize" -> pagination.pageSize.toString)
}
