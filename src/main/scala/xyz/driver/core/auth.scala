package xyz.driver.core

import xyz.driver.core.domain.Email

object auth {

  trait Permission

  final case class Role(id: Id[Role], name: Name[Role])

  trait User {
    def id: Id[User]
    def roles: Set[Role]
  }

  final case class BasicUser(id: Id[User], roles: Set[Role]) extends User

  final case class AuthToken(value: String)
  final case class RefreshToken(value: String)

  final case class PasswordHash(value: String)

  final case class AuthCredentials(email: Email, password: String)
}
