package xyz.driver.core

import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
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

  final case class Macaroon(value: String)

  final case class Base64[T](value: String)

  final case class AuthToken(value: Base64[Macaroon])

  final case class PasswordHash(value: String)

  object AuthService {
    val AuthenticationTokenHeader = "WWW-Authenticate"
  }

  trait AuthService[U <: User] {

    import akka.http.scaladsl.server._
    import Directives._

    protected def authStatus(authToken: AuthToken): OptionT[Future, U]

    def authorize(permissions: Permission*): Directive1[(AuthToken, U)] = {
      parameters('authToken.?).flatMap { parameterTokenValue =>
        optionalHeaderValueByName(AuthService.AuthenticationTokenHeader).flatMap { headerTokenValue =>
          verifyAuthToken(headerTokenValue.orElse(parameterTokenValue), permissions.toSet)
        }
      }
    }

    private def verifyAuthToken(tokenOption: Option[String],
                                permissions: Set[Permission]): Directive1[(AuthToken, U)] =
      tokenOption match {
        case Some(tokenValue) =>
          val token = AuthToken(Base64[Macaroon](tokenValue))

          onComplete(authStatus(token).run).flatMap { tokenUserResult =>
            checkPermissions(tokenUserResult, permissions, token)
          }

        case None =>
          reject(MissingHeaderRejection(AuthService.AuthenticationTokenHeader))
      }

    private def checkPermissions(userResult: Try[Option[U]],
                                 permissions: Set[Permission],
                                 token: AuthToken): Directive1[(AuthToken, U)] = {
      userResult match {
        case Success(Some(user)) =>
          if (permissions.forall(user.permissions.contains)) provide(token -> user)
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
