package xyz.driver.core

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.auth._
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.Future
import scalaz.OptionT

class AuthTest extends FlatSpec with Matchers with MockitoSugar with ScalatestRouteTest {

  val authStatusService: AuthService[User] = new AuthService[User] {
    override def authStatus(context: ServiceRequestContext): OptionT[Future, User] = OptionT.optionT[Future] {
      if (context.contextHeaders.keySet.contains(AuthService.AuthenticationTokenHeader)) {
        Future.successful(Some(new User {
          override def id: Id[User]     = Id[User]("1")
          override def roles: Set[Role] = Set(PathologistRole)
        }: User))
      } else {
        Future.successful(Option.empty[User])
      }
    }
  }

  import authStatusService._

  "'authorize' directive" should "throw error is auth token is not in the request" in {

    Get("/naive/attempt") ~>
      authorize(CanSignOutReport) { user =>
        complete("Never going to be here")
      } ~>
      check {
        // handled shouldBe false
        rejections should contain(ValidationRejection("Wasn't able to find authenticated user for the token provided"))
      }
  }

  it should "throw error is authorized user is not having the requested permission" in {

    val referenceAuthToken = AuthToken("I am a pathologist's token")

    Post("/administration/attempt").addHeader(
      RawHeader(AuthService.AuthenticationTokenHeader, referenceAuthToken.value)
    ) ~>
      authorize(CanAssignRoles) { user =>
        complete("Never going to get here")
      } ~>
      check {
        handled shouldBe false
        rejections should contain(
          AuthenticationFailedRejection(
            CredentialsRejected,
            HttpChallenges.basic("User does not have the required permissions: CanAssignRoles")))
      }
  }

  it should "pass and retrieve the token to client code, if token is in request and user has permission" in {

    val referenceAuthToken = AuthToken("I am token")

    Get("/valid/attempt/?a=2&b=5").addHeader(
      RawHeader(AuthService.AuthenticationTokenHeader, referenceAuthToken.value)
    ) ~>
      authorize(CanSignOutReport) { user =>
        complete("Alright, user \"" + user.id + "\" is authorized")
      } ~>
      check {
        handled shouldBe true
        responseAs[String] shouldBe "Alright, user \"1\" is authorized"
      }
  }
}
