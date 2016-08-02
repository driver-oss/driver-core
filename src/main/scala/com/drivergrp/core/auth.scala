package com.drivergrp.core

object auth {

  final case class FullName[+T](firstName: Name[T], middleName: Name[T], lastName: Name[T])

  final case class Email(username: String, domain: String) {
    override def toString = username + "@" + domain
  }

  trait Role {
    val id: Id[Role]
    val name: Name[Role]

    def canEditReport: Boolean    = false
    def canSignOffReport: Boolean = false
    def canAssignRoles: Boolean   = false
  }

  case object ObserverRole extends Role {
    val id   = Id(1L)
    val name = Name("observer")
  }

  case object PatientRole extends Role {
    val id   = Id(2L)
    val name = Name("patient")
  }

  case object CuratorRole extends Role {
    val id   = Id(3L)
    val name = Name("curator")

    override def canEditReport: Boolean = true
  }

  case object PathologistRole extends Role {
    val id   = Id(4L)
    val name = Name("pathologist")

    override def canEditReport: Boolean    = true
    override def canSignOffReport: Boolean = true
  }

  case object AdministratorRole extends Role {
    val id   = Id(5L)
    val name = Name("administrator")

    override def canEditReport: Boolean    = true
    override def canSignOffReport: Boolean = true
    override def canAssignRoles: Boolean   = true
  }

  final case class Avatar(id: Id[Avatar], name: Name[Avatar])

  final case class User(id: Id[User], name: FullName[User], email: Email, avatar: Option[Avatar], roles: Set[Role])

  val TestUser = User(Id[User](1L),
                      FullName[User](Name("James"), Name("Dewey"), Name("Watson")),
                      Email("j.watson", "uchicago.edu"),
                      Some(Avatar(Id[Avatar](1L), Name[Avatar]("Coolface"))),
                      Set(PathologistRole))

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

  object directives {
    import akka.http.scaladsl.server._
    import Directives._

    val AuthenticationTokenHeader = "WWW-Authenticate"

    def authorize(role: Role): Directive1[Id[User]] = {
      headerValueByName(AuthenticationTokenHeader).flatMap { tokenValue =>
        val token = AuthToken(Base64[Macaroon](tokenValue))

        extractUser(token) match {
          case Some(user) =>
            if (user.roles.contains(role)) provide(user.id)
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

    def extractUser(authToken: AuthToken): Option[User] = {
      Some(TestUser)
    }
  }
}
