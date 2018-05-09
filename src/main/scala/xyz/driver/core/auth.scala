package xyz.driver.core

import xyz.driver.core.domain.Email
import xyz.driver.core.time.Time
import scalaz.Equal

object auth {

  trait Permission

  final case class Role(id: Id[Role], name: Name[Role]) {

    def oneOf(roles: Role*): Boolean = roles.contains(this)

    def oneOf(roles: Set[Role]): Boolean = roles.contains(this)
  }

  object Role {
    implicit def idEqual: Equal[Role] = Equal.equal[Role](_ == _)
  }

  trait User {
    def id: Id[User]
  }

  final case class AuthToken(value: String)

  final case class AuthTokenUserInfo(
      id: Id[User],
      email: Email,
      emailVerified: Boolean,
      audience: String,
      roles: Set[Role],
      expirationTime: Time)
      extends User

  final case class RefreshToken(value: String)
  final case class PermissionsToken(value: String)

  final case class PasswordHash(value: String)

  /**
    * User-provided information for obtaining a session token. [[email]] can be a string representation of a
    * phone number or a email.
    * @param email: a.k.a "user identifier string" internally parsed to either a email or phone number
    */
  final case class AuthCredentials(email: String, password: String)
}
