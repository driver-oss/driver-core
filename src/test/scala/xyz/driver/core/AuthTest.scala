package xyz.driver.core

import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import pdi.jwt.{Jwt, JwtAlgorithm}
import xyz.driver.core.auth._
import xyz.driver.core.domain.Email
import xyz.driver.core.logging._
import xyz.driver.core.rest._

import scala.concurrent.Future
import scalaz.OptionT

class AuthTest extends FlatSpec with Matchers with MockitoSugar with ScalatestRouteTest {

  case object TestRoleAllowedPermission        extends Permission
  case object TestRoleAllowedByTokenPermission extends Permission
  case object TestRoleNotAllowedPermission     extends Permission

  val TestRole = Role(Id("1"), Name("testRole"))

  val (publicKey, privateKey) = {
    import java.security.KeyPairGenerator

    val keygen = KeyPairGenerator.getInstance("RSA")
    keygen.initialize(2048)

    val keyPair = keygen.generateKeyPair()
    (keyPair.getPublic, keyPair.getPrivate)
  }

  val basicAuthorization: Authorization[User] = new Authorization[User] {

    override def userHasPermissions(user: User, permissions: Seq[Permission])(
            implicit ctx: ServiceRequestContext): Future[AuthorizationResult] = {
      val authorized = permissions.map(p => p -> (p === TestRoleAllowedPermission)).toMap
      Future.successful(AuthorizationResult(authorized, ctx.permissionsToken))
    }
  }

  val tokenIssuer        = "users"
  val tokenAuthorization = new CachedTokenAuthorization[User](publicKey, tokenIssuer)

  val authorization = new ChainedAuthorization[User](tokenAuthorization, basicAuthorization)

  val authStatusService = new AuthProvider[User](authorization, NoLogger) {
    override def authenticatedUser(implicit ctx: ServiceRequestContext): OptionT[Future, User] =
      OptionT.optionT[Future] {
        if (ctx.contextHeaders.keySet.contains(AuthProvider.AuthenticationTokenHeader)) {
          Future.successful(Some(AuthTokenUserInfo(
            Id[User]("1"),
            authUserId = Id[AuthUser]("2"),
            Email("foo", "bar"),
            emailVerified = true,
            audience = "driver",
            roles = Set(TestRole)
          )))
        } else {
          Future.successful(Option.empty[User])
        }
      }
  }

  import authStatusService._

  "'authorize' directive" should "throw error if auth token is not in the request" in {

    Get("/naive/attempt") ~>
      authorize(TestRoleAllowedPermission) { user =>
        complete("Never going to be here")
      } ~>
      check {
        // handled shouldBe false
        rejections should contain(ValidationRejection("Wasn't able to find authenticated user for the token provided"))
      }
  }

  it should "throw error if authorized user does not have the requested permission" in {

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
      authorize(TestRoleAllowedPermission) { ctx =>
        complete(s"Alright, user ${ctx.authenticatedUser.id} is authorized")
      } ~>
      check {
        handled shouldBe true
        responseAs[String] shouldBe "Alright, user 1 is authorized"
      }
  }

  it should "authorize permission found in permissions token" in {
    import spray.json._

    val claim = JsObject(
      Map(
        "iss"         -> JsString(tokenIssuer),
        "sub"         -> JsString("1"),
        "permissions" -> JsObject(Map(TestRoleAllowedByTokenPermission.toString -> JsBoolean(true)))
      )).prettyPrint
    val permissionsToken   = PermissionsToken(Jwt.encode(claim, privateKey, JwtAlgorithm.RS256))
    val referenceAuthToken = AuthToken("I am token")

    Get("/alic/attempt/?a=2&b=5")
      .addHeader(RawHeader(AuthProvider.AuthenticationTokenHeader, referenceAuthToken.value))
      .addHeader(RawHeader(AuthProvider.PermissionsTokenHeader, permissionsToken.value)) ~>
      authorize(TestRoleAllowedByTokenPermission) { ctx =>
        complete(s"Alright, user ${ctx.authenticatedUser.id} is authorized by permissions token")
      } ~>
      check {
        handled shouldBe true
        responseAs[String] shouldBe "Alright, user 1 is authorized by permissions token"
      }
  }
}
