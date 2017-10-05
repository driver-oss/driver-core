package xyz.driver.core

import java.sql.SQLException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.github.swagger.akka.SwaggerHttpService._
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.swagger.models.Scheme
import io.swagger.util.Json
import org.slf4j.{LoggerFactory, MDC}
import xyz.driver.core
import xyz.driver.core.rest._
import xyz.driver.core.stats.SystemStats
import xyz.driver.core.time.Time
import xyz.driver.core.time.provider.{SystemTimeProvider, TimeProvider}
import xyz.driver.tracing.TracingDirectives._
import xyz.driver.tracing._

import scala.compat.Platform.ConcurrentModificationException
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.runtime.universe._
import scala.util.Try
import scala.util.control.NonFatal
import scalaz.Scalaz.stringInstance
import scalaz.syntax.equal._

object app {

  class DriverApp(appName: String,
                  version: String,
                  gitHash: String,
                  modules: Seq[Module],
                  time: TimeProvider = new SystemTimeProvider(),
                  log: Logger = Logger(LoggerFactory.getLogger(classOf[DriverApp])),
                  config: Config = core.config.loadDefaultConfig,
                  interface: String = "::0",
                  baseUrl: String = "localhost:8080",
                  scheme: String = "http",
                  port: Int = 8080,
                  tracer: Tracer = NoTracer)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext) {

    implicit private lazy val materializer = ActorMaterializer()(actorSystem)
    private lazy val http                  = Http()(actorSystem)
    val appEnvironment                     = config.getString("application.environment")

    def run(): Unit = {
      activateServices(modules)
      scheduleServicesDeactivation(modules)
      bindHttp(modules)
      Console.print(s"${this.getClass.getName} App is started\n")
    }

    def stop(): Unit = {
      http.shutdownAllConnectionPools().onComplete { _ =>
        Await.result(tracer.close(), 15.seconds) // flush out any remaining traces from the buffer
        val _                 = actorSystem.terminate()
        val terminated        = Await.result(actorSystem.whenTerminated, 30.seconds)
        val addressTerminated = if (terminated.addressTerminated) "is" else "is not"
        Console.print(s"${this.getClass.getName} App $addressTerminated stopped ")
      }
    }

    private def extractHeader(request: HttpRequest)(headerName: String): Option[String] =
      request.headers.find(_.name().toLowerCase === headerName).map(_.value())

    private val allowedHeaders =
      Seq(
        "Origin",
        "X-Requested-With",
        "Content-Type",
        "Content-Length",
        "Accept",
        "X-Trace",
        "Access-Control-Allow-Methods",
        "Access-Control-Allow-Origin",
        "Access-Control-Allow-Headers",
        "Server",
        "Date",
        ContextHeaders.TrackingIdHeader,
        ContextHeaders.TraceHeaderName,
        ContextHeaders.SpanHeaderName,
        ContextHeaders.StacktraceHeader,
        ContextHeaders.AuthenticationTokenHeader,
        "X-Frame-Options",
        "X-Content-Type-Options",
        "Strict-Transport-Security",
        AuthProvider.SetAuthenticationTokenHeader,
        AuthProvider.SetPermissionsTokenHeader
      )

    private def allowOrigin(originHeader: Option[Origin]) =
      `Access-Control-Allow-Origin`(
        originHeader.fold[HttpOriginRange](HttpOriginRange.*)(h => HttpOriginRange(h.origins: _*)))

    protected implicit def rejectionHandler =
      RejectionHandler
        .newBuilder()
        .handleAll[MethodRejection] { rejections =>
          val methods    = rejections map (_.supported)
          lazy val names = methods map (_.name) mkString ", "

          options { ctx =>
            optionalHeaderValueByType[Origin](()) { originHeader =>
              respondWithHeaders(List[HttpHeader](
                Allow(methods),
                `Access-Control-Allow-Methods`(methods),
                allowOrigin(originHeader),
                `Access-Control-Allow-Headers`(allowedHeaders: _*),
                `Access-Control-Expose-Headers`(allowedHeaders: _*)
              )) {
                complete(s"Supported methods: $names.")
              }
            }(ctx)
          } ~
            complete(MethodNotAllowed -> s"HTTP method not allowed, supported methods: $names!")
        }
        .result()

    protected def bindHttp(modules: Seq[Module]): Unit = {
      val serviceTypes   = modules.flatMap(_.routeTypes)
      val swaggerService = swaggerOverride(serviceTypes)
      val swaggerRoutes  = swaggerService.routes ~ swaggerService.swaggerUI
      val versionRt      = versionRoute(version, gitHash, time.currentTime())

      val _ = Future {
        http.bindAndHandle(
          route2HandlerFlow(extractHost { origin =>
            trace(tracer) {
              extractClientIP {
                ip =>
                  optionalHeaderValueByType[Origin](()) {
                    originHeader =>
                      {
                        ctx =>
                          val trackingId = rest.extractTrackingId(ctx.request)
                          MDC.put("trackingId", trackingId)

                          val updatedStacktrace =
                            (rest.extractStacktrace(ctx.request) ++ Array(appName)).mkString("->")
                          MDC.put("stack", updatedStacktrace)

                          storeRequestContextToMdc(ctx.request, origin, ip)

                          def requestLogging: Future[Unit] = Future {
                            log.info(
                              s"""Received request {"method":"${ctx.request.method.value}","url": "${ctx.request.uri}"}""")
                          }

                          val contextWithTrackingId =
                            ctx.withRequest(
                              ctx.request
                                .addHeader(RawHeader(ContextHeaders.TrackingIdHeader, trackingId))
                                .addHeader(RawHeader(ContextHeaders.StacktraceHeader, updatedStacktrace)))

                          handleExceptions(ExceptionHandler(exceptionHandler))({
                            c =>
                              requestLogging.flatMap { _ =>
                                val trackingHeader = RawHeader(ContextHeaders.TrackingIdHeader, trackingId)

                                val responseHeaders = List[HttpHeader](
                                  trackingHeader,
                                  allowOrigin(originHeader),
                                  `Access-Control-Allow-Headers`(allowedHeaders: _*),
                                  `Access-Control-Expose-Headers`(allowedHeaders: _*)
                                )

                                respondWithHeaders(responseHeaders) {
                                  modules.map(_.route).foldLeft(versionRt ~ healthRoute ~ swaggerRoutes)(_ ~ _)
                                }(c)
                              }
                          })(contextWithTrackingId)
                      }
                  }
              }
            }
          }),
          interface,
          port
        )(materializer)
      }
    }

    private def storeRequestContextToMdc(request: HttpRequest, origin: String, ip: RemoteAddress) = {

      MDC.put("origin", origin)
      MDC.put("ip", ip.toOption.map(_.getHostAddress).getOrElse("unknown"))
      MDC.put("remoteHost", ip.toOption.map(_.getHostName).getOrElse("unknown"))

      MDC.put("xForwardedFor",
              extractHeader(request)("x-forwarded-for")
                .orElse(extractHeader(request)("x_forwarded_for"))
                .getOrElse("unknown"))
      MDC.put("remoteAddress", extractHeader(request)("remote-address").getOrElse("unknown"))
      MDC.put("userAgent", extractHeader(request)("user-agent").getOrElse("unknown"))
    }

    protected def swaggerOverride(apiTypes: Seq[Type]) = {
      new Swagger(baseUrl, Scheme.forValue(scheme), version, actorSystem, apiTypes, config) {
        override def generateSwaggerJson: String = {
          import io.swagger.models.Swagger

          import scala.collection.JavaConverters._

          try {
            val swagger: Swagger = reader.read(toJavaTypeSet(apiTypes).asJava)

            // Removing trailing spaces
            swagger.setPaths(
              swagger.getPaths.asScala
                .map {
                  case (key, path) =>
                    key.trim -> path
                }
                .toMap
                .asJava)

            Json.pretty().writeValueAsString(swagger)
          } catch {
            case NonFatal(t) => {
              logger.error("Issue with creating swagger.json", t)
              throw t
            }
          }
        }
      }
    }

    /**
      * Override me for custom exception handling
      *
      * @return Exception handling route for exception type
      */
    protected def exceptionHandler = PartialFunction[Throwable, Route] {

      case is: IllegalStateException =>
        ctx =>
          log.warn(s"Request is not allowed to ${ctx.request.method} ${ctx.request.uri}", is)
          errorResponse(ctx, BadRequest, message = is.getMessage, is)(ctx)

      case cm: ConcurrentModificationException =>
        ctx =>
          log.warn(s"Concurrent modification of the resource ${ctx.request.method} ${ctx.request.uri}", cm)
          errorResponse(ctx, Conflict, "Resource was changed concurrently, try requesting a newer version", cm)(ctx)

      case se: SQLException =>
        ctx =>
          log.warn(s"Database exception for the resource ${ctx.request.method} ${ctx.request.uri}", se)
          errorResponse(ctx, InternalServerError, "Data access error", se)(ctx)

      case t: Throwable =>
        ctx =>
          log.warn(s"Request to ${ctx.request.method} ${ctx.request.uri} could not be handled normally", t)
          errorResponse(ctx, InternalServerError, t.getMessage, t)(ctx)
    }

    protected def errorResponse[T <: Throwable](ctx: RequestContext,
                                                statusCode: StatusCode,
                                                message: String,
                                                exception: T): Route = {

      val trackingId    = rest.extractTrackingId(ctx.request)
      val tracingHeader = RawHeader(ContextHeaders.TrackingIdHeader, rest.extractTrackingId(ctx.request))

      MDC.put("trackingId", trackingId)

      optionalHeaderValueByType[Origin](()) { originHeader =>
        val responseHeaders = List[HttpHeader](tracingHeader,
                                               allowOrigin(originHeader),
                                               `Access-Control-Allow-Headers`(allowedHeaders: _*),
                                               `Access-Control-Expose-Headers`(allowedHeaders: _*))

        respondWithHeaders(responseHeaders) {
          complete(HttpResponse(statusCode, entity = message))
        }
      }
    }

    protected def versionRoute(version: String, gitHash: String, startupTime: Time): Route = {
      import spray.json._
      import DefaultJsonProtocol._
      import SprayJsonSupport._

      path("version") {
        val currentTime = time.currentTime().millis
        complete(
          Map(
            "version"      -> version.toJson,
            "gitHash"      -> gitHash.toJson,
            "modules"      -> modules.map(_.name).toJson,
            "dependencies" -> collectAppDependencies().toJson,
            "startupTime"  -> startupTime.millis.toString.toJson,
            "serverTime"   -> currentTime.toString.toJson,
            "uptime"       -> (currentTime - startupTime.millis).toString.toJson
          ).toJson)
      }
    }

    protected def collectAppDependencies(): Map[String, String] = {

      def serviceWithLocation(serviceName: String): (String, String) =
        serviceName -> Try(config.getString(s"services.$serviceName.baseUrl")).getOrElse("not-detected")

      modules.flatMap(module => module.serviceDiscovery.getUsedServices.map(serviceWithLocation).toSeq).toMap
    }

    protected def healthRoute: Route = {
      import spray.json._
      import DefaultJsonProtocol._
      import SprayJsonSupport._
      import spray.json._

      val memoryUsage = SystemStats.memoryUsage
      val gcStats     = SystemStats.garbageCollectorStats

      path("health") {
        complete(
          Map(
            "availableProcessors" -> SystemStats.availableProcessors.toJson,
            "memoryUsage" -> Map(
              "free"  -> memoryUsage.free.toJson,
              "total" -> memoryUsage.total.toJson,
              "max"   -> memoryUsage.max.toJson
            ).toJson,
            "gcStats" -> Map(
              "garbageCollectionTime"   -> gcStats.garbageCollectionTime.toJson,
              "totalGarbageCollections" -> gcStats.totalGarbageCollections.toJson
            ).toJson,
            "fileSystemSpace" -> SystemStats.fileSystemSpace.map { f =>
              Map("path"        -> f.path.toJson,
                  "freeSpace"   -> f.freeSpace.toJson,
                  "totalSpace"  -> f.totalSpace.toJson,
                  "usableSpace" -> f.usableSpace.toJson)
            }.toJson,
            "operatingSystem" -> SystemStats.operatingSystemStats.toJson
          ))
      }
    }

    /**
      * Initializes services
      */
    protected def activateServices(services: Seq[Module]): Unit = {
      services.foreach { service =>
        Console.print(s"Service ${service.name} starts ...")
        try {
          service.activate()
        } catch {
          case t: Throwable =>
            log.error(s"Service ${service.name} failed to activate", t)
            Console.print(" Failed! (check log)")
        }
        Console.print(" Done\n")
      }
    }

    /**
      * Schedules services to be deactivated on the app shutdown
      */
    protected def scheduleServicesDeactivation(services: Seq[Module]) = {
      Runtime.getRuntime.addShutdownHook(new Thread() {
        override def run(): Unit = {
          services.foreach { service =>
            Console.print(s"Service ${service.name} shutting down ...\n")
            try {
              service.deactivate()
            } catch {
              case t: Throwable =>
                log.error(s"Service ${service.name} failed to deactivate", t)
                Console.print(" Failed! (check log)")
            }
            Console.print(s"Service ${service.name} is shut down\n")
          }
        }
      })
    }
  }

  trait Module {
    val name: String
    def route: Route
    def routeTypes: Seq[Type]

    val serviceDiscovery: ServiceDiscovery with SavingUsedServiceDiscovery = new NoServiceDiscovery()

    def activate(): Unit   = {}
    def deactivate(): Unit = {}
  }

  class EmptyModule extends Module {
    val name         = "Nothing"
    def route: Route = complete(StatusCodes.OK)
    def routeTypes   = Seq.empty[Type]
  }

  class SimpleModule(val name: String, val route: Route, routeType: Type) extends Module {
    def routeTypes: Seq[Type] = Seq(routeType)
  }

  /**
    * Module implementation which may be used to composed a few
    *
    * @param name more general name of the composite module,
    *             must be provided as there is no good way to automatically
    *             generalize the name from the composed modules' names
    * @param modules modules to compose into a single one
    */
  class CompositeModule(val name: String, modules: Seq[Module]) extends Module with RouteConcatenation {

    def route: Route = RouteConcatenation.concat(modules.map(_.route): _*)
    def routeTypes   = modules.flatMap(_.routeTypes)

    override def activate()   = modules.foreach(_.activate())
    override def deactivate() = modules.reverse.foreach(_.deactivate())
  }
}
