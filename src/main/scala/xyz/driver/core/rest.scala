package xyz.driver.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.swagger.models.Scheme
import java.security.PublicKey
import pdi.jwt.{Jwt, JwtAlgorithm}
import xyz.driver.core.auth._
import xyz.driver.core.time.provider.TimeProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.Scalaz.{Id => _, _}
import scalaz.{ListT, OptionT}

package rest {

  object `package` {
    import akka.http.scaladsl.server._
    import Directives._

    def serviceContext: Directive1[ServiceRequestContext] = extract(ctx => extractServiceContext(ctx.request))

    def extractServiceContext(request: HttpRequest): ServiceRequestContext =
      new ServiceRequestContext(extractTrackingId(request), extractContextHeaders(request))

    def extractTrackingId(request: HttpRequest): String = {
      request.headers
        .find(_.name == ContextHeaders.TrackingIdHeader)
        .fold(java.util.UUID.randomUUID.toString)(_.value())
    }

    def extractContextHeaders(request: HttpRequest): Map[String, String] = {
      request.headers.filter { h =>
        h.name === ContextHeaders.AuthenticationTokenHeader || h.name === ContextHeaders.TrackingIdHeader ||
        h.name === ContextHeaders.PermissionsTokenHeader
      } map { header =>
        if (header.name === ContextHeaders.AuthenticationTokenHeader) {
          header.name -> header.value.stripPrefix(ContextHeaders.AuthenticationHeaderPrefix).trim
        } else {
          header.name -> header.value
        }
      } toMap
    }

    private[rest] def escapeScriptTags(byteString: ByteString): ByteString = {
      @annotation.tailrec
      def dirtyIndices(from: Int, descIndices: List[Int]): List[Int] = {
        val index = byteString.indexOf('/', from)
        if (index === -1) descIndices.reverse
        else {
          val (init, tail) = byteString.splitAt(index)
          if ((init endsWith "<") && (tail startsWith "/sc")) {
            dirtyIndices(index + 1, index :: descIndices)
          } else {
            dirtyIndices(index + 1, descIndices)
          }
        }
      }

      val indices = dirtyIndices(0, Nil)

      indices.headOption.fold(byteString) { head =>
        val builder = ByteString.newBuilder
        builder ++= byteString.take(head)

        (indices :+ byteString.length).sliding(2).foreach {
          case Seq(start, end) =>
            builder += ' '
            builder ++= byteString.slice(start, end)
          case Seq(byteStringLength) => // Should not match; sliding on at least 2 elements
            assert(indices.nonEmpty, s"Indices should have been nonEmpty: $indices")
        }
        builder.result
      }
    }

    val sanitizeRequestEntity: Directive0 = {
      mapRequest(
        request => request.mapEntity(entity => entity.transformDataBytes(Flow.fromFunction(escapeScriptTags))))
    }
  }

  class ServiceRequestContext(val trackingId: String = generators.nextUuid().toString,
                              val contextHeaders: Map[String, String] = Map.empty[String, String]) {
    def authToken: Option[AuthToken] =
      contextHeaders.get(AuthProvider.AuthenticationTokenHeader).map(AuthToken.apply)

    def permissionsToken: Option[PermissionsToken] =
      contextHeaders.get(AuthProvider.PermissionsTokenHeader).map(PermissionsToken.apply)

    def withAuthToken(authToken: AuthToken): ServiceRequestContext =
      new ServiceRequestContext(
        trackingId,
        contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value)
      )

    def withAuthenticatedUser[U <: User](authToken: AuthToken, user: U): AuthorizedServiceRequestContext[U] =
      new AuthorizedServiceRequestContext(
        trackingId,
        contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value),
        user
      )

    override def hashCode(): Int =
      Seq[Any](trackingId, contextHeaders).foldLeft(31)((result, obj) => 31 * result + obj.hashCode())

    override def equals(obj: Any): Boolean = obj match {
      case ctx: ServiceRequestContext => trackingId === ctx.trackingId && contextHeaders === ctx.contextHeaders
      case _                          => false
    }

    override def toString: String = s"ServiceRequestContext($trackingId, $contextHeaders)"
  }

  class AuthorizedServiceRequestContext[U <: User](override val trackingId: String = generators.nextUuid().toString,
                                                   override val contextHeaders: Map[String, String] =
                                                     Map.empty[String, String],
                                                   val authenticatedUser: U)
      extends ServiceRequestContext {

    def withPermissionsToken(permissionsToken: PermissionsToken): AuthorizedServiceRequestContext[U] =
      new AuthorizedServiceRequestContext[U](
        trackingId,
        contextHeaders.updated(AuthProvider.PermissionsTokenHeader, permissionsToken.value),
        authenticatedUser)

    override def hashCode(): Int = 31 * super.hashCode() + authenticatedUser.hashCode()

    override def equals(obj: Any): Boolean = obj match {
      case ctx: AuthorizedServiceRequestContext[U] => super.equals(ctx) && ctx.authenticatedUser == authenticatedUser
      case _                                       => false
    }

    override def toString: String =
      s"AuthorizedServiceRequestContext($trackingId, $contextHeaders, $authenticatedUser)"
  }

  object ContextHeaders {
    val AuthenticationTokenHeader  = "Authorization"
    val PermissionsTokenHeader     = "Permissions"
    val AuthenticationHeaderPrefix = "Bearer"
    val TrackingIdHeader           = "X-Trace"
  }

  object AuthProvider {
    val AuthenticationTokenHeader    = ContextHeaders.AuthenticationTokenHeader
    val PermissionsTokenHeader       = ContextHeaders.PermissionsTokenHeader
    val SetAuthenticationTokenHeader = "set-authorization"
    val SetPermissionsTokenHeader    = "set-permissions"
  }

  final case class AuthorizationResult(authorized: Boolean, token: Option[PermissionsToken])
  object AuthorizationResult {
    val unauthorized: AuthorizationResult = AuthorizationResult(authorized = false, None)
  }

  trait Authorization[U <: User] {
    def userHasPermissions(user: U, permissions: Seq[Permission])(
            implicit ctx: ServiceRequestContext): Future[AuthorizationResult]
  }

  class AlwaysAllowAuthorization[U <: User](implicit execution: ExecutionContext) extends Authorization[U] {
    override def userHasPermissions(user: U, permissions: Seq[Permission])(
            implicit ctx: ServiceRequestContext): Future[AuthorizationResult] =
      Future.successful(AuthorizationResult(authorized = true, ctx.permissionsToken))
  }

  class CachedTokenAuthorization[U <: User](publicKey: PublicKey, issuer: String) extends Authorization[U] {
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

        authorized = permissions.forall(p => permissionsMap.get(p.toString).contains(true))
      } yield AuthorizationResult(authorized, Some(token))

      Future.successful(result.getOrElse(AuthorizationResult.unauthorized))
    }
  }

  class ChainedAuthorization[U <: User](authorizations: Authorization[U]*)(implicit execution: ExecutionContext)
      extends Authorization[U] {

    override def userHasPermissions(user: U, permissions: Seq[Permission])(
            implicit ctx: ServiceRequestContext): Future[AuthorizationResult] = {
      authorizations.toList.foldLeftM[Future, AuthorizationResult](AuthorizationResult.unauthorized) {
        (authResult, authorization) =>
          if (authResult.authorized) Future.successful(authResult)
          else authorization.userHasPermissions(user, permissions)
      }
    }
  }

  abstract class AuthProvider[U <: User](val authorization: Authorization[U], log: Logger)(
          implicit execution: ExecutionContext) {

    import akka.http.scaladsl.server._
    import Directives._

    /**
      * Specific implementation on how to extract user from request context,
      * can either need to do a network call to auth server or extract everything from self-contained token
      *
      * @param ctx set of request values which can be relevant to authenticate user
      * @return authenticated user
      */
    def authenticatedUser(implicit ctx: ServiceRequestContext): OptionT[Future, U]

    /**
      * Verifies if request is authenticated and authorized to have `permissions`
      */
    def authorize(permissions: Permission*): Directive1[AuthorizedServiceRequestContext[U]] = {
      serviceContext flatMap { ctx =>
        onComplete {
          (for {
            authToken <- OptionT.optionT(Future.successful(ctx.authToken))
            user      <- authenticatedUser(ctx)
            authCtx = ctx.withAuthenticatedUser(authToken, user)
            authorizationResult <- authorization.userHasPermissions(user, permissions)(authCtx).toOptionT
            cachedPermissionsAuthCtx = authorizationResult.token.fold(authCtx)(authCtx.withPermissionsToken)
          } yield (cachedPermissionsAuthCtx, authorizationResult.authorized)).run
        } flatMap {
          case Success(Some((authCtx, true))) => provide(authCtx)
          case Success(Some((authCtx, false))) =>
            val challenge =
              HttpChallenges.basic(s"User does not have the required permissions: ${permissions.mkString(", ")}")
            log.warn(
              s"User ${authCtx.authenticatedUser} does not have the required permissions: ${permissions.mkString(", ")}")
            reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
          case Success(None) =>
            log.warn(
              s"Wasn't able to find authenticated user for the token provided to verify ${permissions.mkString(", ")}")
            reject(ValidationRejection(s"Wasn't able to find authenticated user for the token provided"))
          case Failure(t) =>
            log.warn(s"Wasn't able to verify token for authenticated user to verify ${permissions.mkString(", ")}", t)
            reject(ValidationRejection(s"Wasn't able to verify token for authenticated user", Some(t)))
        }
      }
    }
  }

  trait Service

  trait RestService extends Service {

    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._

    protected implicit val exec: ExecutionContext
    protected implicit val materializer: ActorMaterializer

    implicit class ResponseEntityFoldable(entity: Unmarshal[ResponseEntity]) {
      def fold[T](default: => T)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] =
        if (entity.value.isKnownEmpty()) Future.successful[T](default) else entity.to[T]
    }

    protected def unitResponse(request: Future[Unmarshal[ResponseEntity]]): OptionT[Future, Unit] =
      OptionT[Future, Unit](request.flatMap(_.to[String]).map(_ => Option(())))

    protected def optionalResponse[T](request: Future[Unmarshal[ResponseEntity]])(
            implicit um: Unmarshaller[ResponseEntity, Option[T]]): OptionT[Future, T] =
      OptionT[Future, T](request.flatMap(_.fold(Option.empty[T])))

    protected def listResponse[T](request: Future[Unmarshal[ResponseEntity]])(
            implicit um: Unmarshaller[ResponseEntity, List[T]]): ListT[Future, T] =
      ListT[Future, T](request.flatMap(_.fold(List.empty[T])))

    protected def jsonEntity(json: JsValue): RequestEntity =
      HttpEntity(ContentTypes.`application/json`, json.compactPrint)

    protected def get(baseUri: Uri, path: String, query: Seq[(String, String)] = Seq.empty) =
      HttpRequest(HttpMethods.GET, endpointUri(baseUri, path, query))

    protected def post(baseUri: Uri, path: String, httpEntity: RequestEntity) =
      HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = httpEntity)

    protected def postJson(baseUri: Uri, path: String, json: JsValue) =
      HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = jsonEntity(json))

    protected def delete(baseUri: Uri, path: String) =
      HttpRequest(HttpMethods.DELETE, endpointUri(baseUri, path))

    protected def endpointUri(baseUri: Uri, path: String) =
      baseUri.withPath(Uri.Path(path))

    protected def endpointUri(baseUri: Uri, path: String, query: Seq[(String, String)]) =
      baseUri.withPath(Uri.Path(path)).withQuery(Uri.Query(query: _*))
  }

  trait ServiceTransport {

    def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse]

    def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]]
  }

  trait ServiceDiscovery {

    def discover[T <: Service](serviceName: Name[Service]): T
  }

  class HttpRestServiceTransport(actorSystem: ActorSystem,
                                 executionContext: ExecutionContext,
                                 log: Logger,
                                 time: TimeProvider)
      extends ServiceTransport {

    protected implicit val materializer = ActorMaterializer()(actorSystem)
    protected implicit val execution    = executionContext

    def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse] = {

      val requestTime = time.currentTime()

      val request = requestStub
        .withHeaders(RawHeader(ContextHeaders.TrackingIdHeader, context.trackingId))
        .withHeaders(context.contextHeaders.toSeq.map { h =>
          RawHeader(h._1, h._2): HttpHeader
        }: _*)

      log.info(s"Sending request to ${request.method} ${request.uri}")

      val response = Http()(actorSystem).singleRequest(request)(materializer)

      response.onComplete {
        case Success(r) =>
          val responseLatency = requestTime.durationTo(time.currentTime())
          log.info(s"Response from ${request.uri} to request $requestStub is successful in $responseLatency ms: $r")

        case Failure(t: Throwable) =>
          val responseLatency = requestTime.durationTo(time.currentTime())
          log.info(s"Failed to receive response from ${request.method} ${request.uri} in $responseLatency ms", t)
          log.warn(s"Failed to receive response from ${request.method} ${request.uri} in $responseLatency ms", t)
      }(executionContext)

      response
    }

    def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]] = {

      sendRequestGetResponse(context)(requestStub) map { response =>
        if (response.status == StatusCodes.NotFound) {
          Unmarshal(HttpEntity.Empty: ResponseEntity)
        } else if (response.status.isFailure()) {
          throw new Exception(
            s"Http status is failure ${response.status} for ${requestStub.method} ${requestStub.uri}")
        } else {
          Unmarshal(response.entity)
        }
      }
    }
  }

  import scala.reflect.runtime.universe._

  class Swagger(override val host: String,
                override val scheme: Scheme,
                version: String,
                override val actorSystem: ActorSystem,
                override val apiTypes: Seq[Type],
                val config: Config)
      extends SwaggerHttpService with HasActorSystem {

    val materializer = ActorMaterializer()(actorSystem)

    override val basePath    = config.getString("swagger.basePath")
    override val apiDocsPath = config.getString("swagger.docsPath")

    override val info = Info(
      config.getString("swagger.apiInfo.description"),
      version,
      config.getString("swagger.apiInfo.title"),
      config.getString("swagger.apiInfo.termsOfServiceUrl"),
      contact = Some(
        Contact(
          config.getString("swagger.apiInfo.contact.name"),
          config.getString("swagger.apiInfo.contact.url"),
          config.getString("swagger.apiInfo.contact.email")
        )),
      license = Some(
        License(
          config.getString("swagger.apiInfo.license"),
          config.getString("swagger.apiInfo.licenseUrl")
        )),
      vendorExtensions = Map.empty[String, AnyRef]
    )

    def swaggerUI = get {
      pathPrefix("") {
        pathEndOrSingleSlash {
          getFromResource("swagger-ui/index.html")
        }
      } ~ getFromResourceDirectory("swagger-ui")
    }
  }
}
