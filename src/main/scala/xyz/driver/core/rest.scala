package xyz.driver.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config
import io.swagger.models.Scheme
import xyz.driver.core.auth._
import xyz.driver.core.logging.Logger
import xyz.driver.core.stats.Stats
import xyz.driver.core.time.TimeRange
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
      ServiceRequestContext(extractTrackingId(request), extractContextHeaders(request))

    def extractTrackingId(request: HttpRequest): String = {
      request.headers
        .find(_.name == ContextHeaders.TrackingIdHeader)
        .fold(java.util.UUID.randomUUID.toString)(_.value())
    }

    def extractContextHeaders(request: HttpRequest): Map[String, String] = {
      request.headers.filter { h =>
        h.name === ContextHeaders.AuthenticationTokenHeader || h.name === ContextHeaders.TrackingIdHeader
      } map { header =>
        if (header.name === ContextHeaders.AuthenticationTokenHeader) {
          header.name -> header.value.stripPrefix(ContextHeaders.AuthenticationHeaderPrefix).trim
        } else {
          header.name -> header.value
        }
      } toMap
    }

    private[rest] def escapeScriptTags(byteString: ByteString): ByteString = {
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

      val firstSlash = byteString.indexOf('/')
      if (firstSlash === -1) byteString
      else {
        val indices = dirtyIndices(firstSlash, Nil) :+ byteString.length
        val builder = ByteString.newBuilder
        builder ++= byteString.take(firstSlash)
        indices.sliding(2).foreach {
          case Seq(start, end) =>
            builder += ' '
            builder ++= byteString.slice(start, end)
        }
        builder.result
      }
    }

    val sanitizeRequestEntity: Directive0 = {
      mapRequest(
        request => request.mapEntity(entity => entity.transformDataBytes(Flow.fromFunction(escapeScriptTags))))
    }
  }

  final case class ServiceRequestContext(trackingId: String = generators.nextUuid().toString,
                                         contextHeaders: Map[String, String] = Map.empty[String, String]) {

    def authToken: Option[AuthToken] =
      contextHeaders.get(AuthProvider.AuthenticationTokenHeader).map(AuthToken.apply)
  }

  object ContextHeaders {
    val AuthenticationTokenHeader  = "Authorization"
    val AuthenticationHeaderPrefix = "Bearer"
    val TrackingIdHeader           = "X-Trace"
  }

  object AuthProvider {
    val AuthenticationTokenHeader    = ContextHeaders.AuthenticationTokenHeader
    val SetAuthenticationTokenHeader = "set-authorization"
  }

  trait Authorization {
    def userHasPermission(user: User, permission: Permission)(implicit ctx: ServiceRequestContext): Future[Boolean]
  }

  class AlwaysAllowAuthorization extends Authorization {
    override def userHasPermission(user: User, permission: Permission)(
            implicit ctx: ServiceRequestContext): Future[Boolean] = {
      Future.successful(true)
    }
  }

  trait AuthProvider[U <: User] {

    import akka.http.scaladsl.server._
    import Directives._

    protected implicit val execution: ExecutionContext
    protected val authorization: Authorization
    protected val log: Logger

    /**
      * Specific implementation on how to extract user from request context,
      * can either need to do a network call to auth server or extract everything from self-contained token
      *
      * @param context set of request values which can be relevant to authenticate user
      * @return authenticated user
      */
    protected def authenticatedUser(context: ServiceRequestContext): OptionT[Future, U]

    def authorize(permissions: Permission*): Directive1[U] = {
      serviceContext flatMap { ctx =>
        onComplete(authenticatedUser(ctx).run flatMap { userOption =>
          userOption.traverse[Future, (U, Boolean)] { user =>
            permissions.toList
              .traverse[Future, Boolean](authorization.userHasPermission(user, _)(ctx))
              .map(results => user -> results.forall(identity))
          }
        }).flatMap {
          case Success(Some((user, authorizationResult))) =>
            if (authorizationResult) provide(user)
            else {
              val challenge =
                HttpChallenges.basic(s"User does not have the required permissions: ${permissions.mkString(", ")}")
              log.error(s"User $user does not have the required permissions: ${permissions.mkString(", ")}")
              reject(AuthenticationFailedRejection(CredentialsRejected, challenge))
            }

          case Success(None) =>
            log.error(s"Wasn't able to find authenticated user for the token provided to verify ${permissions.mkString(", ")}")
            reject(ValidationRejection(s"Wasn't able to find authenticated user for the token provided"))

          case Failure(t) =>
            log.error(s"Wasn't able to verify token for authenticated user to verify ${permissions.mkString(", ")}", t)
            reject(ValidationRejection(s"Wasn't able to verify token for authenticated user", Some(t)))
        }
      }
    }
  }

  trait Service

  trait RestService extends Service {

    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json._
    import DefaultJsonProtocol._

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

    protected def get(baseUri: Uri, path: String) =
      HttpRequest(HttpMethods.GET, endpointUri(baseUri, path))

    protected def get(baseUri: Uri, path: String, query: Map[String, String]) =
      HttpRequest(HttpMethods.GET, endpointUri(baseUri, path, query))

    protected def post(baseUri: Uri, path: String, httpEntity: RequestEntity) =
      HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = httpEntity)

    protected def postJson(baseUri: Uri, path: String, json: JsValue) =
      HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = jsonEntity(json))

    protected def delete(baseUri: Uri, path: String) =
      HttpRequest(HttpMethods.DELETE, endpointUri(baseUri, path))

    protected def endpointUri(baseUri: Uri, path: String) =
      baseUri.withPath(Uri.Path(path))

    protected def endpointUri(baseUri: Uri, path: String, query: Map[String, String]) =
      baseUri.withPath(Uri.Path(path)).withQuery(Uri.Query(query))
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
                                 stats: Stats,
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

      log.audit(s"Sending to ${request.uri} request $request with tracking id ${context.trackingId}")

      val response = Http()(actorSystem).singleRequest(request)(materializer)

      response.onComplete {
        case Success(r) =>
          val responseTime = time.currentTime()
          log.audit(s"Response from ${request.uri} to request $requestStub is successful: $r")
          stats.recordStats(Seq("request", request.uri.toString, "success"), TimeRange(requestTime, responseTime), 1)

        case Failure(t: Throwable) =>
          val responseTime = time.currentTime()
          log.audit(s"Failed to receive response from ${request.uri} to request $requestStub", t)
          log.error(s"Failed to receive response from ${request.uri} to request $requestStub", t)
          stats.recordStats(Seq("request", request.uri.toString, "fail"), TimeRange(requestTime, responseTime), 1)
      }(executionContext)

      response
    }

    def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]] = {

      sendRequestGetResponse(context)(requestStub) map { response =>
        if (response.status == StatusCodes.NotFound) {
          Unmarshal(HttpEntity.Empty: ResponseEntity)
        } else if (response.status.isFailure()) {
          throw new Exception(s"Http status is failure ${response.status}")
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
                val config: Config) extends SwaggerHttpService with HasActorSystem {

    val materializer = ActorMaterializer()(actorSystem)

    override val basePath = config.getString("swagger.basePath")
    override val apiDocsPath = config.getString("swagger.docsPath")

    override val info = Info(
      config.getString("swagger.apiInfo.description"),
      version,
      config.getString("swagger.apiInfo.title"),
      config.getString("swagger.apiInfo.termsOfServiceUrl"),
      contact = Some(Contact(
        config.getString("swagger.apiInfo.contact.name"),
        config.getString("swagger.apiInfo.contact.url"),
        config.getString("swagger.apiInfo.contact.email")
      )),
      license = Some(License(
        config.getString("swagger.apiInfo.license"),
        config.getString("swagger.apiInfo.licenseUrl")
      )),
      vendorExtensions = Map.empty[String, AnyRef])

    def swaggerUI = get {
      pathPrefix("") {
        pathEndOrSingleSlash {
          getFromResource("swagger-ui/index.html")
        }
      } ~ getFromResourceDirectory("swagger-ui")
    }
  }
}
