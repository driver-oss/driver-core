package xyz.driver.core.rest.auth

import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.{ExecutionContext, Future}
import scalaz.Scalaz.{futureInstance, listInstance}
import scalaz.syntax.semigroup._
import scalaz.syntax.traverse._

class ChainedAuthorization[U <: User](authorizations: Authorization[U]*)(implicit execution: ExecutionContext)
    extends Authorization[U] {

  override def userHasPermissions(user: U, permissions: Seq[Permission])(
      implicit ctx: ServiceRequestContext): Future[AuthorizationResult] = {
    def allAuthorized(permissionsMap: Map[Permission, Boolean]): Boolean =
      permissions.forall(permissionsMap.getOrElse(_, false))

    authorizations.toList.foldLeftM[Future, AuthorizationResult](AuthorizationResult.unauthorized) {
      (authResult, authorization) =>
        if (allAuthorized(authResult.authorized)) Future.successful(authResult)
        else {
          authorization.userHasPermissions(user, permissions).map(authResult |+| _)
        }
    }
  }
}
