package xyz.driver.core

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import scalaz.Scalaz.{intInstance, stringInstance}
import scalaz.syntax.equal._

package object rest {
  object ContextHeaders {
    val AuthenticationTokenHeader  = "Authorization"
    val PermissionsTokenHeader     = "Permissions"
    val AuthenticationHeaderPrefix = "Bearer"
    val TrackingIdHeader           = "X-Trace"
    val StacktraceHeader           = "X-Stacktrace"
    val TracingHeader              = trace.TracingHeaderKey
  }

  object AuthProvider {
    val AuthenticationTokenHeader    = ContextHeaders.AuthenticationTokenHeader
    val PermissionsTokenHeader       = ContextHeaders.PermissionsTokenHeader
    val SetAuthenticationTokenHeader = "set-authorization"
    val SetPermissionsTokenHeader    = "set-permissions"
  }

  def serviceContext: Directive1[ServiceRequestContext] = extract(ctx => extractServiceContext(ctx.request))

  def extractServiceContext(request: HttpRequest): ServiceRequestContext =
    new ServiceRequestContext(extractTrackingId(request), extractContextHeaders(request))

  def extractTrackingId(request: HttpRequest): String = {
    request.headers
      .find(_.name == ContextHeaders.TrackingIdHeader)
      .fold(java.util.UUID.randomUUID.toString)(_.value())
  }

  def extractStacktrace(request: HttpRequest): Array[String] =
    request.headers.find(_.name == ContextHeaders.StacktraceHeader).fold("")(_.value()).split("->")

  def extractContextHeaders(request: HttpRequest): Map[String, String] = {
    request.headers.filter { h =>
      h.name === ContextHeaders.AuthenticationTokenHeader || h.name === ContextHeaders.TrackingIdHeader ||
      h.name === ContextHeaders.PermissionsTokenHeader || h.name === ContextHeaders.StacktraceHeader ||
      h.name === ContextHeaders.TracingHeader
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

  val sanitizeRequestEntity: Directive0 = {
    mapRequest(request => request.mapEntity(entity => entity.transformDataBytes(Flow.fromFunction(escapeScriptTags))))
  }
}
