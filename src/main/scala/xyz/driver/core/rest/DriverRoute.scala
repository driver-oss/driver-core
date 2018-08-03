package xyz.driver.core.rest

import java.sql.SQLException

import akka.http.scaladsl.model.headers.CacheDirectives.`no-cache`
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{StatusCodes, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.Logger
import org.slf4j.MDC
import xyz.driver.core.rest
import xyz.driver.core.rest.errors._

import scala.compat.Platform.ConcurrentModificationException

trait DriverRoute {
  def log: Logger

  def route: Route

  def routeWithDefaults: Route = {
    (defaultResponseHeaders & handleExceptions(ExceptionHandler(exceptionHandler))) {
      route
    }
  }

  protected def defaultResponseHeaders: Directive0 = {
    extractRequest flatMap { request =>
      // Needs to happen before any request processing, so all the log messages
      // associated with processing of this request are having this `trackingId`
      val trackingId    = rest.extractTrackingId(request)
      val tracingHeader = RawHeader(ContextHeaders.TrackingIdHeader, trackingId)
      MDC.put("trackingId", trackingId)

      respondWithHeaders(tracingHeader +: DriverRoute.DefaultHeaders: _*)
    }
  }

  /**
    * Override me for custom exception handling
    *
    * @return Exception handling route for exception type
    */
  protected def exceptionHandler: PartialFunction[Throwable, Route] = {
    case serviceException: ServiceException =>
      serviceExceptionHandler(serviceException)

    case is: IllegalStateException =>
      ctx =>
        log.warn(s"Request is not allowed to ${ctx.request.method} ${ctx.request.uri}", is)
        errorResponse(StatusCodes.BadRequest, message = is.getMessage, is)(ctx)

    case cm: ConcurrentModificationException =>
      ctx =>
        log.warn(s"Concurrent modification of the resource ${ctx.request.method} ${ctx.request.uri}", cm)
        errorResponse(StatusCodes.Conflict, "Resource was changed concurrently, try requesting a newer version", cm)(
          ctx)

    case se: SQLException =>
      ctx =>
        log.warn(s"Database exception for the resource ${ctx.request.method} ${ctx.request.uri}", se)
        errorResponse(StatusCodes.InternalServerError, "Data access error", se)(ctx)

    case t: Exception =>
      ctx =>
        log.warn(s"Request to ${ctx.request.method} ${ctx.request.uri} could not be handled normally", t)
        errorResponse(StatusCodes.InternalServerError, t.getMessage, t)(ctx)
  }

  protected def serviceExceptionHandler(serviceException: ServiceException): Route = {
    val statusCode = serviceException match {
      case e: InvalidInputException =>
        log.info("Invalid client input error", e)
        StatusCodes.BadRequest
      case e: InvalidActionException =>
        log.info("Invalid client action error", e)
        StatusCodes.Forbidden
      case e: ResourceNotFoundException =>
        log.info("Resource not found error", e)
        StatusCodes.NotFound
      case e: ExternalServiceException =>
        log.error("Error while calling another service", e)
        StatusCodes.InternalServerError
      case e: ExternalServiceTimeoutException =>
        log.error("Service timeout error", e)
        StatusCodes.GatewayTimeout
      case e: DatabaseException =>
        log.error("Database error", e)
        StatusCodes.InternalServerError
    }

    { (ctx: RequestContext) =>
      import xyz.driver.core.json.serviceExceptionFormat
      val entity =
        HttpEntity(ContentTypes.`application/json`, serviceExceptionFormat.write(serviceException).toString())
      errorResponse(statusCode, entity, serviceException)(ctx)
    }
  }

  protected def errorResponse[T <: Exception](statusCode: StatusCode, message: String, exception: T): Route =
    errorResponse(statusCode, HttpEntity(message), exception)

  protected def errorResponse[T <: Exception](statusCode: StatusCode, entity: ResponseEntity, exception: T): Route = {
    complete(HttpResponse(statusCode, entity = entity))
  }

}

object DriverRoute {
  val DefaultHeaders: List[HttpHeader] = List(
    // This header will eliminate the risk of envoy trying to reuse a connection
    // that already timed out on the server side by completely rejecting keep-alive
    Connection("close"),
    // These 2 headers are the simplest way to prevent IE from caching GET requests
    RawHeader("Pragma", "no-cache"),
    `Cache-Control`(List(`no-cache`(Nil)))
  )
}
