package xyz.driver.core

import akka.http.scaladsl.model.headers.{
  HttpChallenges,
  OAuth2BearerToken,
  RawHeader,
  Authorization => AkkaAuthorization
}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import pdi.jwt.{Jwt, JwtAlgorithm}
import xyz.driver.core.auth._
import xyz.driver.core.domain.Email
import xyz.driver.core.logging._
import xyz.driver.core.rest._
import xyz.driver.core.rest.auth._
import xyz.driver.core.time.Time

import scala.concurrent.Future
import scalaz.OptionT

class AuthTest extends FlatSpec with Matchers with ScalatestRouteTest {

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
          Future.successful(
            Some(
              AuthTokenUserInfo(
                Id[User]("1"),
                Email("foo", "bar"),
                emailVerified = true,
                audience = "driver",
                roles = Set(TestRole),
                expirationTime = Time(1000000L)
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
        rejections should contain(
          AuthenticationFailedRejection(
            AuthenticationFailedRejection.CredentialsMissing,
            HttpChallenges.oAuth2(authStatusService.OAuthRealm)))
      }
  }

  it should "throw error if authorized user does not have the requested permission" in {

    val referenceAuthToken  = AuthToken("I am a test role's token")
    val referenceAuthHeader = AkkaAuthorization(OAuth2BearerToken(referenceAuthToken.value))

    Post("/administration/attempt").addHeader(
      referenceAuthHeader
    ) ~>
      authorize(TestRoleNotAllowedPermission) { user =>
        complete("Never going to get here")
      } ~>
      check {
        handled shouldBe false
        rejections should contain(AuthorizationFailedRejection)
      }
  }

  it should "pass and retrieve the token to client code, if token is in request and user has permission" in {
    val referenceAuthToken  = AuthToken("I am token")
    val referenceAuthHeader = AkkaAuthorization(OAuth2BearerToken(referenceAuthToken.value))

    Get("/valid/attempt/?a=2&b=5").addHeader(
      referenceAuthHeader
    ) ~>
      authorize(TestRoleAllowedPermission) { ctx =>
        complete(s"Alright, user ${ctx.authenticatedUser.id} is authorized")
      } ~>
      check {
        handled shouldBe true
        responseAs[String] shouldBe "Alright, user 1 is authorized"
      }
  }

  it should "authenticate correctly even without the 'Bearer' prefix on the Authorization header" in {
    val referenceAuthToken = AuthToken("unprefixed_token")

    Get("/valid/attempt/?a=2&b=5").addHeader(
      RawHeader(ContextHeaders.AuthenticationTokenHeader, referenceAuthToken.value)
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
    val permissionsToken    = PermissionsToken(Jwt.encode(claim, privateKey, JwtAlgorithm.RS256))
    val referenceAuthToken  = AuthToken("I am token")
    val referenceAuthHeader = AkkaAuthorization(OAuth2BearerToken(referenceAuthToken.value))

    Get("/alic/attempt/?a=2&b=5")
      .addHeader(referenceAuthHeader)
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
