package xyz.driver.core.rest

import java.net.InetAddress
import java.util.UUID

import akka.http.scaladsl.model.headers.HttpChallenge
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive0, Directive1, Directives => AkkaDirectives}
import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.auth.AuthProvider

import scala.util.{Failure, Success, Try}

trait Directives extends AkkaDirectives with PathMatchers {

  def optionalTrackingId: Directive1[Option[String]] = optionalHeaderValueByName(ContextHeaders.TrackingIdHeader)

  // TODO check if the ip we get here is really what we want
  def optionalOriginatingIP: Directive1[Option[InetAddress]] =
    optionalHeaderValueByName(ContextHeaders.OriginatingIpHeader)
      .map {
        case Some(ipName) => Try(InetAddress.getByName(ipName)).toOption
        case None         => None
      }
      .flatMap {
        case Some(ip) => provide(Some(ip))
        case None     => extractClientIP map { _.toOption }
      }

  // TODO should we really keep an ad-hoc map of context headers?
  def contextHeaders: Directive1[Map[String, String]] = extractRequest.map { request =>
    request.headers.filter { h =>
      h.name == ContextHeaders.AuthenticationTokenHeader || h.name == ContextHeaders.TrackingIdHeader ||
      h.name == ContextHeaders.PermissionsTokenHeader || h.name == ContextHeaders.StacktraceHeader ||
      h.name == ContextHeaders.TraceHeaderName || h.name == ContextHeaders.SpanHeaderName ||
      h.name == ContextHeaders.OriginatingIpHeader
    } map { header =>
      // TODO revise this logic
      if (header.name == ContextHeaders.AuthenticationTokenHeader) {
        header.name -> header.value.stripPrefix(ContextHeaders.AuthenticationHeaderPrefix).trim
      } else {
        header.name -> header.value
      }
    } toMap
  }

  /** Extract a service request context from a given request or initializes a new one in cases none is present. */
  def serviceContext: Directive1[ServiceRequestContext] = {
    (optionalTrackingId & optionalOriginatingIP & contextHeaders) tmap {
      case (optTrackingId, optOriginatingIp, headers) =>
        new ServiceRequestContext(
          optTrackingId.getOrElse(UUID.randomUUID.toString),
          optOriginatingIp,
          headers
        )
    }
  }

  def authenticate[U <: User](authenticator: AuthProvider[U]): Directive1[U] = {
    // TODO consider using akka's authenticateOAuth2 directive to extract a token
    // this would also require changing AuthProvider's user checking method as to
    // work simply with a token, not requiring a service context
    serviceContext.flatMap { ctx =>
      onComplete(authenticator.authenticatedUser(ctx).run).flatMap {
        case Success(Some(u)) => provide(u)
        case Success(None) =>
          reject(
            AuthenticationFailedRejection(
              AuthenticationFailedRejection.CredentialsRejected,
              HttpChallenge("token", "Driver")
            ))
        case Failure(ex) =>
          failWith(new RuntimeException("An exception occurred during authentication.", ex))
      }
    }
  }

  def authorize[U <: User](user: U, permissions: Permission*): Directive0 = ???

}

object Directives extends Directives
