package xyz.driver.core.rest.auth

import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.Future

trait Authorization[U <: User] {
  def userHasPermissions(user: U, permissions: Seq[Permission])(
      implicit ctx: ServiceRequestContext): Future[AuthorizationResult]
}
