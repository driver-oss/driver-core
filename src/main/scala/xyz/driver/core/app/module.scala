package xyz.driver.core.app

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.typesafe.scalalogging.Logger
import xyz.driver.core.rest.{DriverRoute, NoServiceDiscovery, SavingUsedServiceDiscovery, ServiceDiscovery}

import scala.reflect.runtime.universe._

trait Module {
  val name: String
  def routes: Seq[DriverRoute]
  def routeTypes: Seq[Type]

  val serviceDiscovery: ServiceDiscovery with SavingUsedServiceDiscovery = new NoServiceDiscovery()

  def activate(): Unit   = {}
  def deactivate(): Unit = {}
}

class EmptyModule extends Module {
  override val name: String = "Nothing"

  override def routes: Seq[DriverRoute] =
    Seq(new DriverRoute {
      override def route: Route = complete(StatusCodes.OK)
      override val log: Logger  = xyz.driver.core.logging.NoLogger
    })

  override def routeTypes: Seq[Type] = Seq.empty[Type]
}

class SimpleModule(override val name: String, route: Route, routeType: Type) extends Module { self =>
  override def routes: Seq[DriverRoute] =
    Seq(new DriverRoute {
      override def route: Route = self.route
      override val log: Logger  = xyz.driver.core.logging.NoLogger
    })
  override def routeTypes: Seq[Type] = Seq(routeType)
}

/**
  * Module implementation which may be used to composed a few
  *
  * @param name    more general name of the composite module,
  *                must be provided as there is no good way to automatically
  *                generalize the name from the composed modules' names
  * @param modules modules to compose into a single one
  */
class CompositeModule(override val name: String, modules: Seq[Module]) extends Module with RouteConcatenation {
  override def routes: Seq[DriverRoute] = modules.flatMap(_.routes)
  override def routeTypes: Seq[Type]    = modules.flatMap(_.routeTypes)
  override def activate(): Unit         = modules.foreach(_.activate())
  override def deactivate(): Unit       = modules.reverse.foreach(_.deactivate())
}
