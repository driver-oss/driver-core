package xyz.driver.core.rest

import xyz.driver.core.auth.{PermissionsToken, User}
import xyz.driver.core.generators

class AuthorizedServiceRequestContext[U <: User](override val trackingId: String = generators.nextUuid().toString,
                                                 override val contextHeaders: Map[String, String] =
                                                   Map.empty[String, String],
                                                 val authenticatedUser: U)
    extends ServiceRequestContext {

  def withPermissionsToken(permissionsToken: PermissionsToken): AuthorizedServiceRequestContext[U] =
    new AuthorizedServiceRequestContext[U](
      trackingId,
      contextHeaders.updated(AuthProvider.PermissionsTokenHeader, permissionsToken.value),
      authenticatedUser)

  override def hashCode(): Int = 31 * super.hashCode() + authenticatedUser.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case ctx: AuthorizedServiceRequestContext[U] => super.equals(ctx) && ctx.authenticatedUser == authenticatedUser
    case _                                       => false
  }

  override def toString: String =
    s"AuthorizedServiceRequestContext($trackingId, $contextHeaders, $authenticatedUser)"
}
