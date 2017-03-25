package xyz.driver.core

import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.auth._
import xyz.driver.core.logging.NoLogger
import xyz.driver.core.rest.{AuthProvider, Authorization, ServiceRequestContext}

import scala.concurrent.Future
import scalaz.OptionT

class AuthTest extends FlatSpec with Matchers with MockitoSugar with ScalatestRouteTest {

  case object TestRoleAllowedPermission    extends Permission
  case object TestRoleNotAllowedPermission extends Permission

  val TestRole = Role(Id("1"), Name("testRole"))

  implicit val exec = scala.concurrent.ExecutionContext.global

  val authorization: Authorization = new Authorization {
    override def userHasPermission(user: User, permission: Permission)(
            implicit ctx: ServiceRequestContext): Future[Boolean] = {
      Future.successful(permission === TestRoleAllowedPermission)
    }
  }

  val authStatusService = new AuthProvider[User](authorization, NoLogger) {

    override def isSessionValid(user: User)(implicit ctx: ServiceRequestContext): Future[Boolean] =
      Future.successful(true)

    override def authenticatedUser(implicit ctx: ServiceRequestContext): OptionT[Future, User] =
      OptionT.optionT[Future] {
        if (ctx.contextHeaders.keySet.contains(AuthProvider.AuthenticationTokenHeader)) {
          Future.successful(Some(BasicUser(Id[User]("1"), Set(TestRole))))
        } else {
          Future.successful(Option.empty[User])
        }
      }
  }

  import authStatusService._

  "'authorize' directive" should "throw error is auth token is not in the request" in {

    Get("/naive/attempt") ~>
      authorize(TestRoleAllowedPermission) { user =>
        complete("Never going to be here")
      } ~>
      check {
        // handled shouldBe false
        rejections should contain(ValidationRejection("Wasn't able to find authenticated user for the token provided"))
      }
  }

  it should "throw error is authorized user is not having the requested permission" in {

    val referenceAuthToken = AuthToken("I am a test role's token")

    Post("/administration/attempt").addHeader(
      RawHeader(AuthProvider.AuthenticationTokenHeader, referenceAuthToken.value)
    ) ~>
      authorize(TestRoleNotAllowedPermission) { user =>
        complete("Never going to get here")
      } ~>
      check {
        handled shouldBe false
        rejections should contain(
          AuthenticationFailedRejection(
            CredentialsRejected,
            HttpChallenges.basic("User does not have the required permissions: TestRoleNotAllowedPermission")))
      }
  }

  it should "pass and retrieve the token to client code, if token is in request and user has permission" in {

    val referenceAuthToken = AuthToken("I am token")

    Get("/valid/attempt/?a=2&b=5").addHeader(
      RawHeader(AuthProvider.AuthenticationTokenHeader, referenceAuthToken.value)
    ) ~>
      authorize(TestRoleAllowedPermission) { user =>
        complete("Alright, user \"" + user.id + "\" is authorized")
      } ~>
      check {
        handled shouldBe true
        responseAs[String] shouldBe "Alright, user \"1\" is authorized"
      }
  }
}
