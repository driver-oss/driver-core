package xyz.driver.core.app

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import xyz.driver.core.database.Database
import xyz.driver.core.rest.{DriverRoute, NoServiceDiscovery, SavingUsedServiceDiscovery, ServiceDiscovery}

import scala.reflect.runtime.universe._

trait Module {
  val name: String
  def route: Route
  def routeTypes: Seq[Type]

  val serviceDiscovery: ServiceDiscovery with SavingUsedServiceDiscovery = new NoServiceDiscovery()

  def activate(): Unit   = {}
  def deactivate(): Unit = {}
}

class EmptyModule extends Module {
  override val name: String = "Nothing"

  override def route: Route          = complete(StatusCodes.OK)
  override def routeTypes: Seq[Type] = Seq.empty[Type]
}

class SimpleModule(override val name: String, theRoute: Route, routeType: Type) extends Module {
  private val driverRoute: DriverRoute = new DriverRoute {
    override def route: Route = theRoute
    override val log: Logger  = xyz.driver.core.logging.NoLogger
  }

  override def route: Route          = driverRoute.routeWithDefaults
  override def routeTypes: Seq[Type] = Seq(routeType)
}

trait SingleDatabaseModule { self: Module =>

  val databaseName: String
  val config: Config

  val database = Database.fromConfig(config, databaseName)

  override def deactivate(): Unit = {
    try {
      database.database.close()
    } finally {
      self.deactivate()
    }
  }
}

/**
  * Module implementation which may be used to compose multiple modules
  *
  * @param name    more general name of the composite module,
  *                must be provided as there is no good way to automatically
  *                generalize the name from the composed modules' names
  * @param modules modules to compose into a single one
  */
class CompositeModule(override val name: String, modules: Seq[Module]) extends Module with RouteConcatenation {
  override def route: Route          = RouteConcatenation.concat(modules.map(_.route): _*)
  override def routeTypes: Seq[Type] = modules.flatMap(_.routeTypes)
  override def activate(): Unit      = modules.foreach(_.activate())
  override def deactivate(): Unit    = modules.reverse.foreach(_.deactivate())
}
