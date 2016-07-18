package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.{ Route, RouteConcatenation }
import akka.stream.ActorMaterializer
import com.drivergrp.core.logging.{ Logger, TypesafeScalaLogger }
import com.drivergrp.core.rest.Swagger
import com.drivergrp.core.time.provider.{ SystemTimeProvider, TimeProvider }
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._

object app {

  class DriverApp(version: String,
                  buildNumber: Int,
                  modules: Seq[Module],
                  time: TimeProvider = new SystemTimeProvider(),
                  log: Logger = new TypesafeScalaLogger(
                      com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(classOf[DriverApp]))),
                  config: Config = com.drivergrp.core.config.loadDefaultConfig,
                  interface: String = "localhost",
                  port: Int = 8080) {

    implicit private lazy val actorSystem      = ActorSystem("spray-routing", config)
    implicit private lazy val executionContext = actorSystem.dispatcher
    implicit private lazy val materializer     = ActorMaterializer()(actorSystem)
    private lazy val http                      = Http()(actorSystem)

    def run() = {
      activateServices(modules)
      scheduleServicesDeactivation(modules)
      bindHttp(modules)
      Console.print(s"${ this.getClass.getName } App is started")
    }

    def stop() = {
      http.shutdownAllConnectionPools().onComplete { _ =>
        val _                 = actorSystem.terminate()
        val terminated        = Await.result(actorSystem.whenTerminated, 30.seconds)
        val addressTerminated = if (terminated.addressTerminated) "is" else "is not"
        Console.print(s"${ this.getClass.getName } App $addressTerminated stopped ")
      }
    }

    protected def bindHttp(modules: Seq[Module]) {
      import SprayJsonSupport._
      import DefaultJsonProtocol._

      val serviceTypes   = modules.flatMap(_.routeTypes)
      val swaggerService = new Swagger(actorSystem, serviceTypes, config)
      val swaggerRoutes  = swaggerService.routes ~ swaggerService.swaggerUI

      val versionRoute = path("version") {
        complete(
            Map(
                "version"     -> version,
                "buildNumber" -> buildNumber.toString,
                "serverTime"  -> time.currentTime().millis.toString
            ))
      }

      val _ = http.bindAndHandle(
          route2HandlerFlow(
              logRequestResult("log")(modules.map(_.route).foldLeft(versionRoute ~ swaggerRoutes)(_ ~ _))),
          interface,
          port)(materializer)
    }

    /**
      * Initializes services
      */
    protected def activateServices(services: Seq[Module]) = {
      services.foreach { service =>
        Console.print(s"Service ${ service.name } starts ...")
        try {
          service.activate()
        } catch {
          case t: Throwable =>
            log.fatal(s"Service ${ service.name } failed to activate", t)
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
            Console.print(s"Service ${ service.name } shutting down ...")
            try {
              service.deactivate()
            } catch {
              case t: Throwable =>
                log.fatal(s"Service ${ service.name } failed to deactivate", t)
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

    def activate(): Unit   = {}
    def deactivate(): Unit = {}
  }

  class EmptyModule extends Module {
    val name = "Nothing"
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
