package xyz.driver.core.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Connection
import akka.http.scaladsl.server.Directives.{complete => akkaComplete}
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.scalalogging.Logger
import org.scalatest.{AsyncFlatSpec, Matchers}
import xyz.driver.core.FutureExtensions
import xyz.driver.core.json.serviceExceptionFormat
import xyz.driver.core.logging.NoLogger
import xyz.driver.core.rest.errors._

import scala.concurrent.Future

class DriverRouteTest
    extends AsyncFlatSpec with ScalatestRouteTest with SprayJsonSupport with Matchers with Directives {
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
      responseAs[ServiceException] shouldBe InvalidInputException()
    }
  }

  it should "respond with a 403 for InvalidActionException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](InvalidActionException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.Forbidden
      responseAs[ServiceException] shouldBe InvalidActionException()
    }
  }

  it should "respond with a 404 for ResourceNotFoundException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](ResourceNotFoundException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.NotFound
      responseAs[ServiceException] shouldBe ResourceNotFoundException()
    }
  }

  it should "respond with a 500 for ExternalServiceException" in {
    val error = ExternalServiceException("GET /api/v1/users/", "Permission denied", None)
    val route = new TestRoute(akkaComplete(Future.failed[String](error)))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.InternalServerError
      responseAs[ServiceException] shouldBe error
    }
  }

  it should "allow pass-through of external service exceptions" in {
    val innerError = InvalidInputException()
    val error      = ExternalServiceException("GET /api/v1/users/", "Permission denied", Some(innerError))
    val future     = Future.failed[String](error)
    val route      = new TestRoute(akkaComplete(future.passThroughExternalServiceException))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.BadRequest
      responseAs[ServiceException] shouldBe innerError
    }
  }

  it should "respond with a 503 for ExternalServiceTimeoutException" in {
    val error = ExternalServiceTimeoutException("GET /api/v1/users/")
    val route = new TestRoute(akkaComplete(Future.failed[String](error)))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.GatewayTimeout
      responseAs[ServiceException] shouldBe error
    }
  }

  it should "respond with a 500 for DatabaseException" in {
    val route = new TestRoute(akkaComplete(Future.failed[String](DatabaseException())))

    Post("/api/v1/foo/bar") ~> route.routeWithDefaults ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.InternalServerError
      responseAs[ServiceException] shouldBe DatabaseException()
    }
  }

  it should "add a `Connection: close` header to avoid clashing with envoy's timeouts" in {
    val rejectionHandler = RejectionHandler.newBuilder().handleNotFound(complete(StatusCodes.NotFound)).result()
    val route            = new TestRoute(handleRejections(rejectionHandler)((get & path("foo"))(complete("OK"))))

    Get("/foo") ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.OK
      headers should contain(Connection("close"))
    }

    Get("/bar") ~> route.routeWithDefaults ~> check {
      status shouldBe StatusCodes.NotFound
      headers should contain(Connection("close"))
    }
  }
}
