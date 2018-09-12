package xyz.driver.core.rest

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpMethod, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.{AsyncFlatSpec, Matchers}
import xyz.driver.core.app.{DriverApp, SimpleModule}

class DriverAppTest extends AsyncFlatSpec with ScalatestRouteTest with Matchers with Directives {
  val config = ConfigFactory.parseString("""
                                           |application {
                                           |   cors {
                                           |     allowedMethods: ["GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS"]
                                           |     allowedOrigins: [{scheme: https, hostSuffix: example.com}]
                                           |   }
                                           |}
                                         """.stripMargin).withFallback(ConfigFactory.load)

  val allowedOrigins = Set(HttpOrigin("https", Host("example.com")))
  val allowedMethods: collection.immutable.Seq[HttpMethod] = {
    import akka.http.scaladsl.model.HttpMethods._
    collection.immutable.Seq(GET, PUT, POST, PATCH, DELETE, OPTIONS)
  }

  import scala.reflect.runtime.universe.typeOf
  class TestApp(testRoute: Route)
      extends DriverApp(
        appName = "test-app",
        version = "0.0.1",
        gitHash = "deadb33f",
        modules = Seq(new SimpleModule("test-module", theRoute = testRoute, routeType = typeOf[DriverApp])),
        config = config,
        log = xyz.driver.core.logging.NoLogger
      )

  it should "respond with the correct CORS headers for the swagger OPTIONS route" in {
    val route = new TestApp(get(complete(StatusCodes.OK)))
    Options(s"/api-docs/swagger.json") ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with the correct CORS headers for the test route" in {
    val route = new TestApp(get(complete(StatusCodes.OK)))
    Get(s"/api/v1/test") ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with the correct CORS headers for a concatenated route" in {
    val route = new TestApp(get(complete(StatusCodes.OK)) ~ post(complete(StatusCodes.OK)))
    Post(s"/api/v1/test") ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "allow subdomains of allowed origin suffixes" in {
    val route = new TestApp(get(complete(StatusCodes.OK)))
    Get(s"/api/v1/test")
      .withHeaders(Origin(HttpOrigin("https", Host("foo.example.com")))) ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOrigin("https", Host("foo.example.com"))))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with default domains for invalid origins" in {
    val route = new TestApp(get(complete(StatusCodes.OK)))
    Get(s"/api/v1/test")
      .withHeaders(Origin(HttpOrigin("https", Host("invalid.foo.bar.com")))) ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with Pragma and Cache-Control (no-cache) headers" in {
    val route = new TestApp(get(complete(StatusCodes.OK)))
    Get(s"/api/v1/test") ~> route.appRoute ~> check {
      status shouldBe StatusCodes.OK
      header("Pragma").map(_.value()) should contain("no-cache")
      header[`Cache-Control`].map(_.value()) should contain("no-cache")
    }
  }
}
