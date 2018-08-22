package xyz.driver.core
package rest
package directives

import akka.http.scaladsl.server.{Directive1, Directives => AkkaDirectives}
import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.auth.AuthProvider

/** Authentication and authorization directives. */
trait AuthDirectives extends AkkaDirectives {

  /** Authenticate a user based on service request headers and check if they have all given permissions. */
  def authenticateAndAuthorize[U <: User](
      authProvider: AuthProvider[U],
      permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
    authProvider.authorize(permissions: _*)
  }

}
