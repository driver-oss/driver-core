package com.drivergrp.core

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected

object auth {

  sealed trait Permission
  case object CanSeeUser                extends Permission
  case object CanSeeAssay               extends Permission
  case object CanSeeReport              extends Permission
  case object CanCreateReport           extends Permission
  case object CanEditReport             extends Permission
  case object CanEditReviewingReport    extends Permission
  case object CanSignOutReport          extends Permission
  case object CanShareReportWithPatient extends Permission
  case object CanAssignRoles            extends Permission

  trait Role {
    val id: Id[Role]
    val name: Name[Role]
    val permissions: Set[Permission]

    def hasPermission(permission: Permission): Boolean = permissions.contains(permission)
  }

  case object ObserverRole extends Role {
    val id          = Id(1L)
    val name        = Name("observer")
    val permissions = Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport)
  }

  case object PatientRole extends Role {
    val id          = Id(2L)
    val name        = Name("patient")
    val permissions = Set.empty[Permission]
  }

  case object CuratorRole extends Role {
    val id          = Id(3L)
    val name        = Name("curator")
    val permissions = Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport, CanEditReport)
  }

  case object PathologistRole extends Role {
    val id   = Id(4L)
    val name = Name("pathologist")
    val permissions =
      Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport, CanEditReport, CanSignOutReport, CanEditReviewingReport)
  }

  case object AdministratorRole extends Role {
    val id   = Id(5L)
    val name = Name("administrator")
    val permissions = Set[Permission](
        CanSeeUser,
        CanSeeAssay,
        CanSeeReport,
        CanCreateReport,
        CanEditReport,
        CanEditReviewingReport,
        CanSignOutReport,
        CanShareReportWithPatient,
        CanAssignRoles
    )
  }

  trait User {
    def id: Id[User]
    def roles: Set[Role]
    def permissions: Set[Permission] = roles.flatMap(_.permissions)
  }

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

  final case class PasswordHash(value: String)

  def extractUser(authToken: AuthToken): User = {
    new User() {
      override def id: Id[User]     = Id[User](1L)
      override def roles: Set[Role] = Set(PathologistRole)
    }

    // TODO: or reject(ValidationRejection(s"Wasn't able to extract user for the token provided")) if none
  }

  object directives {
    import akka.http.scaladsl.server._
    import Directives._

    val AuthenticationTokenHeader = "WWW-Authenticate"

    def authorize(permission: Permission): Directive1[AuthToken] = {
      parameters('authToken.?).flatMap { parameterTokenValue =>
        optionalHeaderValueByName(AuthenticationTokenHeader).flatMap { headerTokenValue =>
          headerTokenValue.orElse(parameterTokenValue) match {
            case Some(tokenValue) =>
              val token = AuthToken(Base64[Macaroon](tokenValue))

              if (extractUser(token).roles.exists(_.hasPermission(permission))) provide(token)
              else {
                val challenge = HttpChallenges.basic(s"User does not have the required permission $permission")
                reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
              }

            case None =>
              reject(MissingHeaderRejection("WWW-Authenticate"))
          }
        }
      }
    }
  }
}
