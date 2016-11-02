package xyz.driver.core

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scalaz.OptionT

object auth {

  sealed trait Permission
  case object CanSeeUser                extends Permission
  case object CanSeeAssay               extends Permission
  case object CanSeeReport              extends Permission
  case object CanCreateReport           extends Permission
  case object CanEditReport             extends Permission
  case object CanReviewReport           extends Permission
  case object CanEditReviewingReport    extends Permission
  case object CanSignOutReport          extends Permission
  case object CanAmendReport            extends Permission
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
    val permissions = ObserverRole.permissions ++ Set[Permission](CanEditReport, CanReviewReport)
  }

  case object PathologistRole extends Role {
    val id   = Id(4L)
    val name = Name("pathologist")
    val permissions = ObserverRole.permissions ++
        Set[Permission](CanEditReport, CanSignOutReport, CanAmendReport, CanEditReviewingReport)
  }

  case object AdministratorRole extends Role {
    val id   = Id(5L)
    val name = Name("administrator")
    val permissions = CuratorRole.permissions ++
        Set[Permission](CanCreateReport, CanShareReportWithPatient, CanAssignRoles)
  }

  trait User {
    def id: Id[User]
    def roles: Set[Role]
    def permissions: Set[Permission] = roles.flatMap(_.permissions)
  }

  final case class AuthToken(value: String)

  final case class PasswordHash(value: String)

  object AuthService {
    val AuthenticationTokenHeader    = rest.ContextHeaders.AuthenticationTokenHeader
    val SetAuthenticationTokenHeader = "set-authorization"
  }

  trait AuthService[U <: User] {

    import akka.http.scaladsl.server._
    import Directives._

    protected def authStatus(context: ServiceRequestContext): OptionT[Future, U]

    def authorize(permissions: Permission*): Directive1[U] = {
      rest.serviceContext flatMap { ctx =>
        onComplete(authStatus(ctx).run).flatMap {
          case Success(Some(user)) =>
            if (permissions.forall(user.permissions.contains)) provide(user)
            else {
              val challenge =
                HttpChallenges.basic(s"User does not have the required permissions: ${permissions.mkString(", ")}")
              reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
            }

          case Success(None) =>
            reject(ValidationRejection(s"Wasn't able to find authenticated user for the token provided"))

          case Failure(t) =>
            reject(ValidationRejection(s"Wasn't able to verify token for authenticated user", Some(t)))
        }
      }
    }
  }
}
