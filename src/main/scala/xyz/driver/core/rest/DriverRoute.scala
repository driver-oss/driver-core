package xyz.driver.core.rest

import java.sql.SQLException

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, ExceptionHandler, RequestContext, Route}
import com.typesafe.scalalogging.Logger
import org.slf4j.MDC
import xyz.driver.core.rest
import xyz.driver.core.rest.errors._

import scala.compat.Platform.ConcurrentModificationException

trait DriverRoute {
  def log: Logger

  def route: Route

  def routeWithDefaults: Route = {
    (defaultResponseHeaders & handleExceptions(ExceptionHandler(exceptionHandler)))(route)
  }

  protected def defaultResponseHeaders: Directive0 = {
    (extractRequest & optionalHeaderValueByType[Origin](())) tflatMap {
      case (request, originHeader) =>
        val tracingHeader = RawHeader(ContextHeaders.TrackingIdHeader, rest.extractTrackingId(request))
        val responseHeaders = List[HttpHeader](
          tracingHeader,
          allowOrigin(originHeader),
          `Access-Control-Allow-Headers`(AllowedHeaders: _*),
          `Access-Control-Expose-Headers`(AllowedHeaders: _*)
        )

        respondWithHeaders(responseHeaders)
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
        errorResponse(ctx, StatusCodes.BadRequest, message = is.getMessage, is)(ctx)

    case cm: ConcurrentModificationException =>
      ctx =>
        log.warn(s"Concurrent modification of the resource ${ctx.request.method} ${ctx.request.uri}", cm)
        errorResponse(ctx,
                      StatusCodes.Conflict,
                      "Resource was changed concurrently, try requesting a newer version",
                      cm)(ctx)

    case se: SQLException =>
      ctx =>
        log.warn(s"Database exception for the resource ${ctx.request.method} ${ctx.request.uri}", se)
        errorResponse(ctx, StatusCodes.InternalServerError, "Data access error", se)(ctx)

    case t: Exception =>
      ctx =>
        log.warn(s"Request to ${ctx.request.method} ${ctx.request.uri} could not be handled normally", t)
        errorResponse(ctx, StatusCodes.InternalServerError, t.getMessage, t)(ctx)
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
      case e: ExternalServiceTimeoutException =>
        log.error("Service timeout error", e)
        StatusCodes.GatewayTimeout
      case e: DatabaseException =>
        log.error("Database error", e)
        StatusCodes.InternalServerError
    }

    { (ctx: RequestContext) =>
      errorResponse(ctx, statusCode, serviceException.message, serviceException)(ctx)
    }
  }

  protected def errorResponse[T <: Exception](ctx: RequestContext,
                                              statusCode: StatusCode,
                                              message: String,
                                              exception: T): Route = {
    val trackingId = rest.extractTrackingId(ctx.request)
    MDC.put("trackingId", trackingId)
    complete(HttpResponse(statusCode, entity = message))
  }
}
