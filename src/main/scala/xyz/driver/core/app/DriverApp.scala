package xyz.driver.core.app

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.swagger.models.Scheme
import org.slf4j.{LoggerFactory, MDC}
import xyz.driver.core
import xyz.driver.core.rest._
import xyz.driver.core.stats.SystemStats
import xyz.driver.core.time.Time
import xyz.driver.core.time.provider.{SystemTimeProvider, TimeProvider}
import xyz.driver.tracing.TracingDirectives._
import xyz.driver.tracing._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try
import scalaz.Scalaz.stringInstance
import scalaz.syntax.equal._

class DriverApp(
    appName: String,
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
  self =>

  implicit private lazy val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
  private lazy val http: HttpExt                            = Http()(actorSystem)
  val appEnvironment: String                                = config.getString("application.environment")

  def run(): Unit = {
    activateServices(modules)
    scheduleServicesDeactivation(modules)
    bindHttp(modules)
    Console.print(s"${this.getClass.getName} App is started\n")
  }

  def stop(): Unit = {
    http.shutdownAllConnectionPools().onComplete { _ =>
      Await.result(tracer.close(), 15.seconds) // flush out any remaining traces from the buffer
      val terminated        = Await.result(actorSystem.terminate(), 30.seconds)
      val addressTerminated = if (terminated.addressTerminated) "is" else "is not"
      Console.print(s"${this.getClass.getName} App $addressTerminated stopped ")
    }
  }

  protected lazy val allowedCorsDomainSuffixes: Set[HttpOrigin] = {
    import scala.collection.JavaConverters._
    config
      .getConfigList("application.cors.allowedOrigins")
      .asScala
      .map { c =>
        HttpOrigin(c.getString("scheme"), Host(c.getString("hostSuffix")))
      }(scala.collection.breakOut)
  }

  protected lazy val defaultCorsAllowedMethods: Set[HttpMethod] = {
    import scala.collection.JavaConverters._
    config.getStringList("application.cors.allowedMethods").asScala.toSet.flatMap(HttpMethods.getForKey)
  }

  protected lazy val defaultCorsAllowedOrigin: Origin = {
    Origin(allowedCorsDomainSuffixes.to[collection.immutable.Seq])
  }

  protected def corsAllowedOriginHeader(origin: Option[Origin]): HttpHeader = {
    val allowedOrigin =
      origin
        .filter { requestOrigin =>
          allowedCorsDomainSuffixes.exists { allowedOriginSuffix =>
            requestOrigin.origins.exists(o =>
              o.scheme == allowedOriginSuffix.scheme &&
                o.host.host.address.endsWith(allowedOriginSuffix.host.host.address()))
          }
        }
        .getOrElse(defaultCorsAllowedOrigin)

    `Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigin.origins: _*))
  }

  protected def respondWithAllCorsHeaders: Directive0 = {
    respondWithCorsAllowedHeaders tflatMap { _ =>
      respondWithCorsAllowedMethodHeaders(defaultCorsAllowedMethods) tflatMap { _ =>
        optionalHeaderValueByType[Origin](()) flatMap { origin =>
          respondWithHeader(corsAllowedOriginHeader(origin))
        }
      }
    }
  }

  private def extractHeader(request: HttpRequest)(headerName: String): Option[String] =
    request.headers.find(_.name().toLowerCase === headerName).map(_.value())

  protected def defaultOptionsRoute: Route = options {
    respondWithAllCorsHeaders {
      complete("OK")
    }
  }

  def appRoute: Route = {
    val serviceTypes   = modules.flatMap(_.routeTypes)
    val swaggerService = new Swagger(baseUrl, Scheme.forValue(scheme) :: Nil, version, serviceTypes, config, log)
    val swaggerRoute   = swaggerService.routes ~ swaggerService.swaggerUI
    val versionRt      = versionRoute(version, gitHash, time.currentTime())
    val basicRoutes = new DriverRoute {
      override def log: Logger  = self.log
      override def route: Route = versionRt ~ healthRoute ~ swaggerRoute
    }
    val combinedRoute =
      Route.seal(modules.map(_.route).foldLeft(basicRoutes.routeWithDefaults)(_ ~ _) ~ defaultOptionsRoute)

    (extractHost & extractClientIP & trace(tracer)) {
      case (origin, ip) =>
        ctx =>
          val trackingId = extractTrackingId(ctx.request)
          MDC.put("trackingId", trackingId)

          val updatedStacktrace =
            (extractStacktrace(ctx.request) ++ Array(appName)).mkString("->")
          MDC.put("stack", updatedStacktrace)

          storeRequestContextToMdc(ctx.request, origin, ip)

          log.debug(s"""Received request {"method":"${ctx.request.method.value}","url": "${ctx.request.uri}"}""")

          val contextWithTrackingId =
            ctx.withRequest(
              ctx.request
                .addHeader(RawHeader(ContextHeaders.TrackingIdHeader, trackingId))
                .addHeader(RawHeader(ContextHeaders.StacktraceHeader, updatedStacktrace)))

          respondWithAllCorsHeaders {
            combinedRoute
          }(contextWithTrackingId)
    }
  }

  protected def bindHttp(modules: Seq[Module]): Unit = {
    val _ = http.bindAndHandle(route2HandlerFlow(appRoute), interface, port)(materializer)
  }

  private def storeRequestContextToMdc(request: HttpRequest, origin: String, ip: RemoteAddress): Unit = {

    MDC.put("origin", origin)
    MDC.put("ip", ip.toOption.map(_.getHostAddress).getOrElse("unknown"))
    MDC.put("remoteHost", ip.toOption.map(_.getHostName).getOrElse("unknown"))

    MDC.put(
      "xForwardedFor",
      extractHeader(request)("x-forwarded-for")
        .orElse(extractHeader(request)("x_forwarded_for"))
        .getOrElse("unknown"))
    MDC.put("remoteAddress", extractHeader(request)("remote-address").getOrElse("unknown"))
    MDC.put("userAgent", extractHeader(request)("user-agent").getOrElse("unknown"))
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
            Map(
              "path"        -> f.path.toJson,
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
  protected def scheduleServicesDeactivation(services: Seq[Module]): Unit = {
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
