package xyz.driver.core.rest

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethods, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.app.{DriverApp, Module}

import scala.reflect.runtime.universe._

class DriverAppTest extends FlatSpec with ScalatestRouteTest with Matchers {
  class TestRoute extends DriverRoute {
    override def log: Logger = xyz.driver.core.logging.NoLogger
    override def route: Route = path("api" / "v1" / "test")(post(complete("OK")))
  }

  val module: Module = new Module {
    val testRoute = new TestRoute
    override def route: Route = testRoute.routeWithDefaults
    override def routeTypes: Seq[Type] = Seq(typeOf[TestRoute])
    override val name: String = "test-module"
  }

  val app: DriverApp = new DriverApp(
    appName = "test-app",
    version = "0.1",
    gitHash = "deadb33f",
    modules = Seq(module)
  )

  val config: Config = xyz.driver.core.config.loadDefaultConfig
  val routingSettings: RoutingSettings = RoutingSettings(config)
  val appRoute: Route = Route.seal(app.appRoute)(routingSettings = routingSettings, rejectionHandler = DriverApp.rejectionHandler)

  "DriverApp" should "respond with the correct CORS headers for the swagger OPTIONS route" in {
    Options(s"/api-docs/swagger.json") ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      info(response.toString())
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange.*))
      headers should contain(`Access-Control-Allow-Methods`(HttpMethods.GET))
    }
  }

  it should "respond with the correct CORS headers for the test route" in {
    Options(s"/api/v1/test") ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      info(response.toString())
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange.*))
      headers should contain(`Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST))
    }
  }
}
