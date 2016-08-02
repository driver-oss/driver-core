package com.drivergrp.core

object auth {

  trait Permission

  trait Role {
    val id: Id[Role]
    val name: Name[Role]

    def hasPermission(permission: Permission): Boolean = false
  }

  trait User {
    def id: Id[User]
    def roles: Set[Role]
  }

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

  object directives {
    import akka.http.scaladsl.server._
    import Directives._

    val AuthenticationTokenHeader = "WWW-Authenticate"

    type UserExtractor = AuthToken => Option[User]

    def authorize(role: Role)(implicit userExtractor: UserExtractor): Directive1[Id[User]] = {
      headerValueByName(AuthenticationTokenHeader).flatMap { tokenValue =>
        val token = AuthToken(Base64[Macaroon](tokenValue))

        userExtractor(token) match {
          case Some(user) =>
            if (user.roles.contains(role)) provide(user.id: Id[User])
            else reject(ValidationRejection(s"User does not have the required ${role.name} role"))
          case None =>
            reject(ValidationRejection(s"Wasn't able to extract user for the token provided"))
        }
      }
    }

    def extractToken: Directive1[AuthToken] = {
      headerValueByName(AuthenticationTokenHeader).flatMap { token =>
        provide(AuthToken(Base64[Macaroon](token)))
      }
    }
  }
}
