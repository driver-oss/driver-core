package xyz.driver.core.rest

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, StatusCodes}
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
    override def log: Logger    = xyz.driver.core.logging.NoLogger
    override def config: Config = xyz.driver.core.config.loadDefaultConfig
    override def route: Route   = path("api" / "v1" / "test")(post(complete("OK")))
  }

  val module: Module = new Module {
    val testRoute                      = new TestRoute
    override def route: Route          = testRoute.routeWithDefaults
    override def routeTypes: Seq[Type] = Seq(typeOf[TestRoute])
    override val name: String          = "test-module"
  }

  val app: DriverApp = new DriverApp(
    appName = "test-app",
    version = "0.1",
    gitHash = "deadb33f",
    modules = Seq(module),
    log = xyz.driver.core.logging.NoLogger
  )

  val config: Config                   = xyz.driver.core.config.loadDefaultConfig
  val routingSettings: RoutingSettings = RoutingSettings(config)
  val appRoute: Route                  = Route.seal(app.appRoute)(routingSettings = routingSettings)

  val allowedMethods: collection.immutable.Seq[HttpMethod] = {
    import scala.collection.JavaConverters._
    config
      .getStringList("application.cors.allowedMethods")
      .asScala
      .flatMap(HttpMethods.getForKey)
      .to[collection.immutable.Seq]
  }

  val allowedOrigin: Origin = {
    import scala.collection.JavaConverters._
    Origin(
      config
        .getConfigList("application.cors.allowedOrigins")
        .asScala
        .map { c =>
          HttpOrigin(c.getString("scheme"), Host(c.getString("hostSuffix")))
        }(scala.collection.breakOut): _*)
  }

  "DriverApp" should "respond with the correct CORS headers for the swagger OPTIONS route" in {
    Options(s"/api-docs/swagger.json") ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigin.origins: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with the correct CORS headers for the test route" in {
    Options(s"/api/v1/test") ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigin.origins: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "allow subdomains of allowed origin suffixes" in {
    Options(s"/api/v1/test").withHeaders(Origin(HttpOrigin("https", Host("foo.example.com")))) ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOrigin("https", Host("foo.example.com"))))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with default domains for invalid origins" in {
    Options(s"/api/v1/test")
      .withHeaders(Origin(HttpOrigin("https", Host("invalid.foo.bar.com")))) ~> appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigin.origins: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }
}
