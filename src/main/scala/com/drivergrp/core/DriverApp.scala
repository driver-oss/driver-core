package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.stream.ActorMaterializer
import com.drivergrp.core.logging.{Logger, TypesafeScalaLogger}
import com.drivergrp.core.module.Module
import com.typesafe.config.Config
import org.slf4j.LoggerFactory


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
