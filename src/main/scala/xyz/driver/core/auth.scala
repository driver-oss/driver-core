package xyz.driver.core

import xyz.driver.core.domain.Email

import scalaz.Equal

object auth {

  trait Permission

  final case class Role(id: Id[Role], name: Name[Role])

  object Role {
    implicit def idEqual: Equal[Role] = Equal.equal[Role](_ == _)
  }

  trait User {
    def id: Id[User]
    def roles: Set[Role]
  }

  final case class BasicUser(id: Id[User], roles: Set[Role]) extends User

  final case class AuthToken(value: String)
  final case class RefreshToken(value: String)
  final case class PermissionsToken(value: String)

  final case class PasswordHash(value: String)

  final case class AuthCredentials(email: Email, password: String)
}
