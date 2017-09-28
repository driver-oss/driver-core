package xyz.driver.core.rest.auth

import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.Future

class AlwaysAllowAuthorization[U <: User] extends Authorization[U] {
  override def userHasPermissions(user: U, permissions: Seq[Permission])(
          implicit ctx: ServiceRequestContext): Future[AuthorizationResult] = {
    val permissionsMap = permissions.map(_ -> true).toMap
    Future.successful(AuthorizationResult(authorized = permissionsMap, ctx.permissionsToken))
  }
}
