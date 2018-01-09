package xyz.driver.core.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete => akkaComplete}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.scalalogging.Logger
import org.scalatest.{AsyncFlatSpec, Matchers}
import xyz.driver.core.logging.NoLogger
import xyz.driver.core.rest.errors._

import scala.concurrent.Future

class DriverRouteTest extends AsyncFlatSpec with ScalatestRouteTest with Matchers {
  class TestRoute(override val route: Route) extends DriverRoute {
    override def log: Logger = NoLogger
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
}
