package xyz.driver.core

import xyz.driver.core.domain.{Email, PhoneNumber}

import scalaz.Equal

object auth {

  trait Permission

  final case class Role(id: Id[Role], name: Name[Role])

  object Role {
    implicit def idEqual: Equal[Role] = Equal.equal[Role](_ == _)
  }

  trait User {
    def id: Id[User]
  }

  final case class AuthToken(value: String)

  final case class AuthUser(id: Id[AuthUser],
                            userId: Id[User],
                            email: Email,
                            emailVerified: Boolean,
                            phoneNumber: Option[PhoneNumber],
                            phoneVerified: Boolean,
                            isBlocked: Boolean,
                            roles: Set[Role])

  final case class AuthTokenUserInfo(id: Id[User],
                                     authUserId: Id[AuthUser],
                                     email: Email,
                                     emailVerified: Boolean,
                                     audience: String,
                                     roles: Set[Role])
      extends User

  final case class RefreshToken(value: String)
  final case class PermissionsToken(value: String)

  final case class PasswordHash(value: String)

  final case class AuthCredentials(email: Email, password: String)
}
