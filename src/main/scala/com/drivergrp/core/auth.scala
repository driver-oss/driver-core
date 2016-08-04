package com.drivergrp.core

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

    def hasPermission(permission: Permission): Boolean = false
  }

  case object ObserverRole extends Role {
    val id   = Id(1L)
    val name = Name("observer")

    override def hasPermission(permission: Permission): Boolean =
      Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport).contains(permission)
  }

  case object PatientRole extends Role {
    val id   = Id(2L)
    val name = Name("patient")
  }

  case object CuratorRole extends Role {
    val id   = Id(3L)
    val name = Name("curator")

    override def hasPermission(permission: Permission): Boolean =
      Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport, CanEditReport).contains(permission)
  }

  case object PathologistRole extends Role {
    val id   = Id(4L)
    val name = Name("pathologist")

    override def hasPermission(permission: Permission): Boolean =
      Set[Permission](CanSeeUser, CanSeeAssay, CanSeeReport, CanEditReport, CanSignOutReport, CanEditReviewingReport)
        .contains(permission)
  }

  case object AdministratorRole extends Role {
    val id   = Id(5L)
    val name = Name("administrator")

    override def hasPermission(permission: Permission): Boolean = true
  }

  trait User {
    def id: Id[User]
    def roles: Set[Role]
  }

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

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
      headerValueByName(AuthenticationTokenHeader).flatMap { tokenValue =>
        val token = AuthToken(Base64[Macaroon](tokenValue))

        if (extractUser(token).roles.exists(_.hasPermission(permission))) provide(token)
        else reject(ValidationRejection(s"User does not have the required permission $permission"))
      }
    }
  }
}
