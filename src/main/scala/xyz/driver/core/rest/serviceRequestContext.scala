package xyz.driver.core.rest

import java.net.InetAddress

import xyz.driver.core.auth.{AuthToken, PermissionsToken, User}
import xyz.driver.core.generators
import scalaz.Scalaz.{mapEqual, stringInstance}
import scalaz.syntax.equal._
import xyz.driver.core.rest.auth.AuthProvider

class ServiceRequestContext(
    val trackingId: String = generators.nextUuid().toString,
    val originatingIp: Option[InetAddress] = None,
    val contextHeaders: Map[String, String] = Map.empty[String, String]) {
  def authToken: Option[AuthToken] =
    contextHeaders.get(AuthProvider.AuthenticationTokenHeader).map(AuthToken.apply)

  def permissionsToken: Option[PermissionsToken] =
    contextHeaders.get(AuthProvider.PermissionsTokenHeader).map(PermissionsToken.apply)

  def withAuthToken(authToken: AuthToken): ServiceRequestContext =
    new ServiceRequestContext(
      trackingId,
      originatingIp,
      contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value)
    )

  def withAuthenticatedUser[U <: User](authToken: AuthToken, user: U): AuthorizedServiceRequestContext[U] =
    new AuthorizedServiceRequestContext(
      trackingId,
      originatingIp,
      contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value),
      user
    )

  override def hashCode(): Int =
    Seq[Any](trackingId, originatingIp, contextHeaders)
      .foldLeft(31)((result, obj) => 31 * result + obj.hashCode())

  override def equals(obj: Any): Boolean = obj match {
    case ctx: ServiceRequestContext =>
      trackingId === ctx.trackingId &&
        originatingIp == originatingIp &&
        contextHeaders === ctx.contextHeaders
    case _ => false
  }

  override def toString: String = s"ServiceRequestContext($trackingId, $contextHeaders)"
}

class AuthorizedServiceRequestContext[U <: User](
    override val trackingId: String = generators.nextUuid().toString,
    override val originatingIp: Option[InetAddress] = None,
    override val contextHeaders: Map[String, String] = Map.empty[String, String],
    val authenticatedUser: U)
    extends ServiceRequestContext {

  def withPermissionsToken(permissionsToken: PermissionsToken): AuthorizedServiceRequestContext[U] =
    new AuthorizedServiceRequestContext[U](
      trackingId,
      originatingIp,
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
