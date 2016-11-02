package xyz.driver.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config
import xyz.driver.core.logging.Logger
import xyz.driver.core.stats.Stats
import xyz.driver.core.time.TimeRange
import xyz.driver.core.time.provider.TimeProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.Scalaz.{Id => _, _}

object rest {

  object ContextHeaders {
    val AuthenticationTokenHeader = "WWW-Authenticate"
    val TrackingIdHeader          = "l5d-ctx-trace" // https://linkerd.io/doc/0.7.4/linkerd/protocol-http/
  }

  final case class ServiceRequestContext(
    trackingId: String = generators.nextUuid().toString,
    contextHeaders: Map[String, String] = Map.empty[String, String])

  import akka.http.scaladsl.server._
  import Directives._

  def serviceContext: Directive1[ServiceRequestContext] = extract(ctx => extractServiceContext(ctx))

  def extractServiceContext(ctx: RequestContext): ServiceRequestContext =
    ServiceRequestContext(extractTrackingId(ctx), extractContextHeaders(ctx))

  def extractTrackingId(ctx: RequestContext): String = {
    ctx.request.headers
      .find(_.name == ContextHeaders.TrackingIdHeader)
      .fold(java.util.UUID.randomUUID.toString)(_.value())
  }

  def extractContextHeaders(ctx: RequestContext): Map[String, String] = {
    ctx.request.headers.filter { h =>
      h.lowercaseName.startsWith("l5d-") || h.name === ContextHeaders.AuthenticationTokenHeader
    } map { header =>
      header.name -> header.value
    } toMap
  }


  trait Service

  trait ServiceTransport {

    def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]]
  }

  trait ServiceDiscovery {

    def discover[T <: Service](serviceName: Name[Service]): T
  }

  class HttpRestServiceTransport(actorSystem: ActorSystem, executionContext: ExecutionContext,
                                 log: Logger, stats: Stats, time: TimeProvider) extends ServiceTransport {

    protected implicit val materializer = ActorMaterializer()(actorSystem)
    protected implicit val execution = executionContext

    def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]] = {

      val requestTime = time.currentTime()

      val request = requestStub
        .withHeaders(RawHeader(ContextHeaders.TrackingIdHeader, context.trackingId))
        .withHeaders(context.contextHeaders.toSeq.map { h => RawHeader(h._1, h._2): HttpHeader }: _*)

      log.audit(s"Sending to ${request.uri} request $request with tracking id ${context.trackingId}")

      val responseEntity = Http()(actorSystem).singleRequest(request)(materializer) map { response =>
        if(response.status == StatusCodes.NotFound) {
          Unmarshal(HttpEntity.Empty: ResponseEntity)
        } else if(response.status.isFailure()) {
          throw new Exception(s"Http status is failure ${response.status}")
        } else {
          Unmarshal(response.entity)
        }
      }

      responseEntity.onComplete {
        case Success(r) =>
          val responseTime = time.currentTime()
          log.audit(s"Response from ${request.uri} to request $requestStub is successful")
          stats.recordStats(Seq("request", request.uri.toString, "success"), TimeRange(requestTime, responseTime), 1)

        case Failure(t: Throwable) =>
          val responseTime = time.currentTime()
          log.audit(s"Failed to receive response from ${request.uri} to request $requestStub")
          log.error(s"Failed to receive response from ${request.uri} to request $requestStub", t)
          stats.recordStats(Seq("request", request.uri.toString, "fail"), TimeRange(requestTime, responseTime), 1)
      } (executionContext)

      responseEntity
    }
  }

  import scala.reflect.runtime.universe._

  class Swagger(override val host: String,
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
