package xyz.driver.core.rest.auth

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import com.typesafe.scalalogging.Logger
import xyz.driver.core._
import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.{AuthorizedServiceRequestContext, ServiceRequestContext, serviceContext}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import scalaz.Scalaz.futureInstance
import scalaz.OptionT

abstract class AuthProvider[U <: User](val authorization: Authorization[U], log: Logger)(
    implicit execution: ExecutionContext) {

  import akka.http.scaladsl.server._
  import Directives._

  /**
    * Specific implementation on how to extract user from request context,
    * can either need to do a network call to auth server or extract everything from self-contained token
    *
    * @param ctx set of request values which can be relevant to authenticate user
    * @return authenticated user
    */
  def authenticatedUser(implicit ctx: ServiceRequestContext): OptionT[Future, U]

  /**
    * Verifies if a service context is authenticated and authorized to have `permissions`
    */
  def authorize(
      context: ServiceRequestContext,
      permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
    onComplete {
      (for {
        authToken <- OptionT.optionT(Future.successful(context.authToken))
        user      <- authenticatedUser(context)
        authCtx = context.withAuthenticatedUser(authToken, user)
        authorizationResult <- authorization.userHasPermissions(user, permissions)(authCtx).toOptionT

        cachedPermissionsAuthCtx = authorizationResult.token.fold(authCtx)(authCtx.withPermissionsToken)
        allAuthorized            = permissions.forall(authorizationResult.authorized.getOrElse(_, false))
      } yield (cachedPermissionsAuthCtx, allAuthorized)).run
    } flatMap {
      case Success(Some((authCtx, true))) => provide(authCtx)
      case Success(Some((authCtx, false))) =>
        val challenge =
          HttpChallenges.basic(s"User does not have the required permissions: ${permissions.mkString(", ")}")
        log.warn(
          s"User ${authCtx.authenticatedUser} does not have the required permissions: ${permissions.mkString(", ")}")
        reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
      case Success(None) =>
        log.warn(
          s"Wasn't able to find authenticated user for the token provided to verify ${permissions.mkString(", ")}")
        reject(ValidationRejection(s"Wasn't able to find authenticated user for the token provided"))
      case Failure(t) =>
        log.warn(s"Wasn't able to verify token for authenticated user to verify ${permissions.mkString(", ")}", t)
        reject(ValidationRejection(s"Wasn't able to verify token for authenticated user", Some(t)))
    }
  }

  /**
    * Verifies if request is authenticated and authorized to have `permissions`
    */
  def authorize(permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
    serviceContext flatMap { ctx =>
      authorize(ctx, permissions: _*)
    }
  }
}
