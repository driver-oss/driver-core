package xyz.driver.core.rest.auth

import akka.http.scaladsl.server.directives.Credentials
import com.typesafe.scalalogging.Logger
import scalaz.OptionT
import xyz.driver.core.auth.{AuthToken, Permission, User}
import xyz.driver.core.rest.{AuthorizedServiceRequestContext, ServiceRequestContext, serviceContext}

import scala.concurrent.{ExecutionContext, Future}

abstract class AuthProvider[U <: User](val authorization: Authorization[U], log: Logger)(
    implicit execution: ExecutionContext) {

  import akka.http.scaladsl.server._
  import Directives.{authorize => akkaAuthorize, _}

  // TODO(zsmith) look up what this should be
  val OAuthRealm = "driver.xyz"

  /**
    * Specific implementation on how to extract user from request context,
    * can either need to do a network call to auth server or extract everything from self-contained token
    *
    * @param ctx set of request values which can be relevant to authenticate user
    * @return authenticated user
    */
  def authenticatedUser(implicit ctx: ServiceRequestContext): OptionT[Future, U]

  protected def authenticator(context: ServiceRequestContext): AsyncAuthenticator[U] = {
    case Credentials.Missing =>
      log.info(s"Request (${context.trackingId}) missing authentication credentials")
      Future.successful(None)
    case Credentials.Provided(authToken) =>
      authenticatedUser(context.withAuthToken(AuthToken(authToken))).run
  }

  /**
    * Verifies that a user agent is properly authenticated, and (optionally) authorized with the specified permissions
    */
  def authorize(
      context: ServiceRequestContext,
      permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
    authenticateOAuth2Async[U](OAuthRealm, authenticator(context)) flatMap { authenticatedUser =>
      val authCtx = context.withAuthenticatedUser(context.authToken.get, authenticatedUser)
      onSuccess(authorization.userHasPermissions(authenticatedUser, permissions)(authCtx)) flatMap {
        case AuthorizationResult(authorized, token) =>
          val allAuthorized = permissions.forall(authorized.getOrElse(_, false))
          akkaAuthorize(allAuthorized) tflatMap { _ =>
            val cachedPermissionsCtx = token.fold(authCtx)(authCtx.withPermissionsToken)
            provide(cachedPermissionsCtx)
          }
      }
    }
  }

  /**
    * Verifies if request is authenticated and authorized to have `permissions`
    */
  def authorize(permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
    serviceContext flatMap (authorize(_, permissions: _*))
  }
}
