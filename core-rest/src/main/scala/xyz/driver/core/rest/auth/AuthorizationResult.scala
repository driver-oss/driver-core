package xyz.driver.core.rest.auth

import xyz.driver.core.auth.{Permission, PermissionsToken}

import scalaz.Scalaz.mapMonoid
import scalaz.Semigroup
import scalaz.syntax.semigroup._

final case class AuthorizationResult(authorized: Map[Permission, Boolean], token: Option[PermissionsToken])
object AuthorizationResult {
  val unauthorized: AuthorizationResult = AuthorizationResult(authorized = Map.empty, None)

  implicit val authorizationSemigroup: Semigroup[AuthorizationResult] = new Semigroup[AuthorizationResult] {
    private implicit val authorizedBooleanSemigroup = Semigroup.instance[Boolean](_ || _)
    private implicit val permissionsTokenSemigroup =
      Semigroup.instance[Option[PermissionsToken]]((a, b) => b.orElse(a))

    override def append(a: AuthorizationResult, b: => AuthorizationResult): AuthorizationResult = {
      AuthorizationResult(a.authorized |+| b.authorized, a.token |+| b.token)
    }
  }
}
