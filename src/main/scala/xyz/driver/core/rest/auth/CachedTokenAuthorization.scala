package xyz.driver.core.rest.auth

import java.nio.file.{Files, Path}
import java.security.{KeyFactory, PublicKey}
import java.security.spec.X509EncodedKeySpec

import pdi.jwt.{Jwt, JwtAlgorithm}
import xyz.driver.core.auth.{Permission, User}
import xyz.driver.core.rest.ServiceRequestContext

import scala.concurrent.Future
import scalaz.syntax.std.boolean._

class CachedTokenAuthorization[U <: User](publicKey: => PublicKey, issuer: String) extends Authorization[U] {
  override def userHasPermissions(user: U, permissions: Seq[Permission])(
      implicit ctx: ServiceRequestContext): Future[AuthorizationResult] = {
    import spray.json._

    def extractPermissionsFromTokenJSON(tokenObject: JsObject): Option[Map[String, Boolean]] =
      tokenObject.fields.get("permissions").collect {
        case JsObject(fields) =>
          fields.collect {
            case (key, JsBoolean(value)) => key -> value
          }
      }

    val result = for {
      token <- ctx.permissionsToken
      jwt   <- Jwt.decode(token.value, publicKey, Seq(JwtAlgorithm.RS256)).toOption
      jwtJson = jwt.parseJson.asJsObject

      // Ensure jwt is for the currently authenticated user and the correct issuer, otherwise return None
      _ <- jwtJson.fields.get("sub").contains(JsString(user.id.value)).option(())
      _ <- jwtJson.fields.get("iss").contains(JsString(issuer)).option(())

      permissionsMap <- extractPermissionsFromTokenJSON(jwtJson)

      authorized = permissions.map(p => p -> permissionsMap.getOrElse(p.toString, false)).toMap
    } yield AuthorizationResult(authorized, Some(token))

    Future.successful(result.getOrElse(AuthorizationResult.unauthorized))
  }
}

object CachedTokenAuthorization {
  def apply[U <: User](publicKeyFile: Path, issuer: String): CachedTokenAuthorization[U] = {
    lazy val publicKey: PublicKey = {
      val publicKeyBase64Encoded = new String(Files.readAllBytes(publicKeyFile)).trim
      val publicKeyBase64Decoded = java.util.Base64.getDecoder.decode(publicKeyBase64Encoded)
      val spec                   = new X509EncodedKeySpec(publicKeyBase64Decoded)
      KeyFactory.getInstance("RSA").generatePublic(spec)
    }
    new CachedTokenAuthorization[U](publicKey, issuer)
  }
}
