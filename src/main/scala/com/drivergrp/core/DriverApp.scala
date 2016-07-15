package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.stream.ActorMaterializer
import com.drivergrp.core.config.ConfigModule
import com.drivergrp.core.logging.LoggerModule


trait DriverApp {
  this: ConfigModule with LoggerModule =>

  def interface: String = "localhost"
  def port: Int = 8080

  def services: Seq[Service]


  val servicesInstances = services
  activateServices(servicesInstances)
  scheduleServicesDeactivation(servicesInstances)

  implicit val actorSystem = ActorSystem("spray-routing", config)
  implicit val executionContext = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()(actorSystem)

  val serviceTypes = servicesInstances.flatMap(_.serviceTypes)
  val swaggerService = new Swagger(actorSystem, serviceTypes, config)
  val swaggerRoutes = swaggerService.routes ~ swaggerService.swaggerUI

  Http()(actorSystem).bindAndHandle(
    route2HandlerFlow(logRequestResult("log")(servicesInstances.map(_.route).foldLeft(swaggerRoutes) { _ ~ _ })),
    interface, port)(materializer)


  /**
    * Initializes services
    */
  protected def activateServices(servicesInstances: Seq[Service]) = {
    servicesInstances.foreach { service =>
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
  protected def scheduleServicesDeactivation(servicesInstances: Seq[Service]) = {
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        servicesInstances.foreach { service =>
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
