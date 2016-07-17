package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.stream.ActorMaterializer
import com.drivergrp.core.logging.{Logger, TypesafeScalaLogger}
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.drivergrp.core.rest.Swagger
import com.typesafe.config.Config
import org.slf4j.LoggerFactory


object app {

  class DriverApp(modules: Seq[Module],
                  log: Logger = new TypesafeScalaLogger(
                    com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(classOf[DriverApp]))),
                  config: Config = com.drivergrp.core.config.loadDefaultConfig,
                  interface: String = "localhost", port: Int = 8080) {

    def run() = {
      activateServices(modules)
      scheduleServicesDeactivation(modules)
      bindHttp(modules)
    }

    protected def bindHttp(modules: Seq[Module]) {
      implicit val actorSystem = ActorSystem("spray-routing", config)
      implicit val executionContext = actorSystem.dispatcher
      implicit val materializer = ActorMaterializer()(actorSystem)

      val serviceTypes = modules.flatMap(_.routeTypes)
      val swaggerService = new Swagger(actorSystem, serviceTypes, config)
      val swaggerRoutes = swaggerService.routes ~ swaggerService.swaggerUI

      Http()(actorSystem).bindAndHandle(
        route2HandlerFlow(logRequestResult("log")(modules.map(_.route).foldLeft(swaggerRoutes) { _ ~ _ })),
        interface, port)(materializer)
    }

    /**
      * Initializes services
      */
    protected def activateServices(services: Seq[Module]) = {
      services.foreach { service =>
        Console.print(s"Service ${service.name} starts ...")
        try {
          service.activate()
        } catch {
          case t: Throwable =>
            log.fatal(s"Service ${service.name} failed to activate", t)
            Console.print(" Failed! (check log)")
        }
        Console.println(" Done")
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
            Console.println(" Done")
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

    def activate(): Unit = {}
    def deactivate(): Unit = {}
  }

  class EmptyModule extends Module {
    val name = "Nothing"
    def route: Route = complete(StatusCodes.OK)
    def routeTypes = Seq.empty[Type]
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
  class CompositeModule(val name: String, modules: Seq[Module])
    extends Module with RouteConcatenation {

    def route: Route = modules.map(_.route).reduce(_ ~ _)
    def routeTypes = modules.flatMap(_.routeTypes)

    override def activate() = modules.foreach(_.activate())
    override def deactivate() = modules.reverse.foreach(_.deactivate())
  }
}
