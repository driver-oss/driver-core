package xyz.driver.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.{ExceptionHandler, Route, RouteConcatenation}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol
import xyz.driver.core
import xyz.driver.core.logging.{Logger, TypesafeScalaLogger}
import xyz.driver.core.rest.Swagger
import xyz.driver.core.stats.SystemStats
import xyz.driver.core.time.Time
import xyz.driver.core.time.provider.{SystemTimeProvider, TimeProvider}

import scala.compat.Platform.ConcurrentModificationException
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object app {

  class DriverApp(version: String,
                  gitHash: String,
                  modules: Seq[Module],
                  time: TimeProvider = new SystemTimeProvider(),
                  log: Logger = new TypesafeScalaLogger(
                    com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(classOf[DriverApp]))),
                  config: Config = core.config.loadDefaultConfig,
                  interface: String = "::0",
                  baseUrl: String = "localhost:8080",
                  port: Int = 8080) {

    implicit private lazy val actorSystem      = ActorSystem("spray-routing", config)
    implicit private lazy val executionContext = actorSystem.dispatcher
    implicit private lazy val materializer     = ActorMaterializer()(actorSystem)
    private lazy val http                      = Http()(actorSystem)

    def run(): Unit = {
      activateServices(modules)
      scheduleServicesDeactivation(modules)
      bindHttp(modules)
      Console.print(s"${this.getClass.getName} App is started\n")
    }

    def stop(): Unit = {
      http.shutdownAllConnectionPools().onComplete { _ =>
        val _                 = actorSystem.terminate()
        val terminated        = Await.result(actorSystem.whenTerminated, 30.seconds)
        val addressTerminated = if (terminated.addressTerminated) "is" else "is not"
        Console.print(s"${this.getClass.getName} App $addressTerminated stopped ")
      }
    }

    protected def bindHttp(modules: Seq[Module]): Unit = {
      val serviceTypes   = modules.flatMap(_.routeTypes)
      val swaggerService = new Swagger(baseUrl, version, actorSystem, serviceTypes, config)
      val swaggerRoutes  = swaggerService.routes ~ swaggerService.swaggerUI
      val versionRt      = versionRoute(version, gitHash, time.currentTime())

      val generalExceptionHandler = ExceptionHandler {

        case is: IllegalStateException =>
          extractUri { uri =>
            // TODO: extract `requestUuid` from request or thread, provided by linkerd/zipkin
            def requestUuid = java.util.UUID.randomUUID.toString

            log.debug(s"Request is not allowed to $uri ($requestUuid)", is)
            complete(
              HttpResponse(BadRequest,
                           entity = s"""{ "requestUuid": "$requestUuid", "message": "${is.getMessage}" }"""))
          }

        case cm: ConcurrentModificationException =>
          extractUri { uri =>
            // TODO: extract `requestUuid` from request or thread, provided by linkerd/zipkin
            def requestUuid = java.util.UUID.randomUUID.toString

            log.debug(s"Concurrent modification of the resource $uri ($requestUuid)", cm)
            complete(
              HttpResponse(Conflict, entity = s"""{ "requestUuid": "$requestUuid", "message": "${cm.getMessage}" }"""))
          }

        case t: Throwable =>
          extractUri { uri =>
            // TODO: extract `requestUuid` from request or thread, provided by linkerd/zipkin
            def requestUuid = java.util.UUID.randomUUID.toString

            log.error(s"Request to $uri could not be handled normally ($requestUuid)", t)
            complete(
              HttpResponse(InternalServerError,
                           entity = s"""{ "requestUuid": "$requestUuid", "message": "${t.getMessage}" }"""))
          }
      }

      val _ = Future {
        http.bindAndHandle(route2HandlerFlow(handleExceptions(generalExceptionHandler) {
          logRequestResult("log")(modules.map(_.route).foldLeft(versionRt ~ healthRoute ~ swaggerRoutes)(_ ~ _))
        }), interface, port)(materializer)
      }
    }

    protected def versionRoute(version: String, gitHash: String, startupTime: Time): Route = {
      import DefaultJsonProtocol._
      import SprayJsonSupport._

      path("version") {
        val currentTime = time.currentTime().millis
        complete(
          Map(
            "version"     -> version,
            "gitHash"     -> gitHash,
            "modules"     -> modules.map(_.name).mkString(", "),
            "startupTime" -> startupTime.millis.toString,
            "serverTime"  -> currentTime.toString,
            "uptime"      -> (currentTime - startupTime.millis).toString
          ))
      }
    }

    protected def healthRoute: Route = {
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
            log.fatal(s"Service ${service.name} failed to activate", t)
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
            Console.print(s"Service ${service.name} shutting down ...")
            try {
              service.deactivate()
            } catch {
              case t: Throwable =>
                log.fatal(s"Service ${service.name} failed to deactivate", t)
                Console.print(" Failed! (check log)")
            }
            Console.print(" Done\n")
          }
        }
      })
    }
  }

  import scala.reflect.runtime.universe._

  trait Module {
    val name: String
    def route: Route
    def routeTypes: Seq[Type]

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

    def route: Route = modules.map(_.route).reduce(_ ~ _)
    def routeTypes   = modules.flatMap(_.routeTypes)

    override def activate()   = modules.foreach(_.activate())
    override def deactivate() = modules.reverse.foreach(_.deactivate())
  }
}
