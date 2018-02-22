package xyz.driver.core.rest

import akka.http.scaladsl.model.{HttpMethod, StatusCodes}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives.{complete => akkaComplete}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.scalatest.{AsyncFlatSpec, Matchers}
import xyz.driver.core.logging.NoLogger
import xyz.driver.core.rest.errors._

import scala.concurrent.Future

class DriverRouteTest extends AsyncFlatSpec with ScalatestRouteTest with Matchers with Directives {
  class TestRoute(override val route: Route) extends DriverRoute {
    override def log: Logger = NoLogger
    override def config: Config =
      ConfigFactory.parseString("""
                                  |application {
                                  |   cors {
                                  |     allowedMethods: ["GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS"]
                                  |     allowedOrigins: [{scheme: https, hostSuffix: example.com}]
                                  |   }
                                  |}
      """.stripMargin)
  }

  val allowedOrigins = Set(HttpOrigin("https", Host("example.com")))
  val allowedMethods: collection.immutable.Seq[HttpMethod] = {
    import akka.http.scaladsl.model.HttpMethods._
    collection.immutable.Seq(GET, PUT, POST, PATCH, DELETE, OPTIONS)
  }

  "DriverRoute" should "respond with 200 OK for a basic route" in {
    val route = new TestRoute(akkaComplete(StatusCodes.OK))

    Get("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.OK
    }
  }

  it should "respond with a 401 for an InvalidInputException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](InvalidInputException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldBe "Invalid input"
    }
  }

  it should "respond with a 403 for InvalidActionException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](InvalidActionException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.Forbidden
      responseAs[String] shouldBe "This action is not allowed"
    }
  }

  it should "respond with a 404 for ResourceNotFoundException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](ResourceNotFoundException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.NotFound
      responseAs[String] shouldBe "Resource not found"
    }
  }

  it should "respond with a 500 for ExternalServiceException" in {
    val error = ExternalServiceException("GET /api/v1/users/", "Permission denied")
    val route = new TestRoute(akkaComplete(Future.failed[String](error)))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.InternalServerError
      responseAs[String] shouldBe "Error while calling 'GET /api/v1/users/': Permission denied"
    }
  }

  it should "respond with a 503 for ExternalServiceTimeoutException" in {
    val error = ExternalServiceTimeoutException("GET /api/v1/users/")
    val route = new TestRoute(akkaComplete(Future.failed[String](error)))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.GatewayTimeout
      responseAs[String] shouldBe "GET /api/v1/users/ took too long to respond"
    }
  }

  it should "respond with a 500 for DatabaseException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](DatabaseException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.InternalServerError
      responseAs[String] shouldBe "Database access error"
    }
  }

  it should "respond with the correct CORS headers for the swagger OPTIONS route" in {
    val route = new TestRoute(get(akkaComplete(StatusCodes.OK)))
    Options(s"/api-docs/swagger.json") ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with the correct CORS headers for the test route" in {
    val route = new TestRoute(get(akkaComplete(StatusCodes.OK)))
    Options(s"/api/v1/test") ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "allow subdomains of allowed origin suffixes" in {
    val route = new TestRoute(get(akkaComplete(StatusCodes.OK)))
    Options(s"/api/v1/test")
      .withHeaders(Origin(HttpOrigin("https", Host("foo.example.com")))) ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOrigin("https", Host("foo.example.com"))))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }

  it should "respond with default domains for invalid origins" in {
    val route = new TestRoute(get(akkaComplete(StatusCodes.OK)))
    Options(s"/api/v1/test")
      .withHeaders(Origin(HttpOrigin("https", Host("invalid.foo.bar.com")))) ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(`Access-Control-Allow-Origin`(HttpOriginRange(allowedOrigins.toSeq: _*)))
      header[`Access-Control-Allow-Methods`].get.methods should contain theSameElementsAs allowedMethods
    }
  }
}
