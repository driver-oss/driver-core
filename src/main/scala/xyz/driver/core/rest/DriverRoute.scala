package xyz.driver.core.rest

import java.sql.SQLException

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Conflict, InternalServerError}
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, RequestContext, Route}
import com.typesafe.scalalogging.Logger
import org.slf4j.MDC
import xyz.driver.core.rest
import xyz.driver.core.rest.errors.ServiceException

import scala.compat.Platform.ConcurrentModificationException

trait DriverRoute {
  val log: Logger

  def route: Route

  def routeWithDefaults: Route = handleExceptions(ExceptionHandler(exceptionHandler)) {
    route
  }

  /**
    * Override me for custom exception handling
    *
    * @return Exception handling route for exception type
    */
  protected def exceptionHandler: PartialFunction[Throwable, Route] = {
    case api: ServiceException if api.isPatientSensitive =>
      ctx =>
        log.info("PHI Sensitive error")
        errorResponse(ctx, InternalServerError, "Server error", api)(ctx)

    case api: ServiceException =>
      ctx =>
        log.info("API Error")
        errorResponse(ctx, api.statusCode, api.message, api)(ctx)

    case is: IllegalStateException =>
      ctx =>
        log.warn(s"Request is not allowed to ${ctx.request.method} ${ctx.request.uri}", is)
        errorResponse(ctx, BadRequest, message = is.getMessage, is)(ctx)

    case cm: ConcurrentModificationException =>
      ctx =>
        log.warn(s"Concurrent modification of the resource ${ctx.request.method} ${ctx.request.uri}", cm)
        errorResponse(ctx, Conflict, "Resource was changed concurrently, try requesting a newer version", cm)(ctx)

    case se: SQLException =>
      ctx =>
        log.warn(s"Database exception for the resource ${ctx.request.method} ${ctx.request.uri}", se)
        errorResponse(ctx, InternalServerError, "Data access error", se)(ctx)

    case t: Throwable =>
      ctx =>
        log.warn(s"Request to ${ctx.request.method} ${ctx.request.uri} could not be handled normally", t)
        errorResponse(ctx, InternalServerError, t.getMessage, t)(ctx)
  }

  protected def errorResponse[T <: Throwable](ctx: RequestContext,
                                              statusCode: StatusCode,
                                              message: String,
                                              exception: T): Route = {

    val trackingId    = rest.extractTrackingId(ctx.request)
    val tracingHeader = RawHeader(ContextHeaders.TrackingIdHeader, rest.extractTrackingId(ctx.request))

    MDC.put("trackingId", trackingId)

    optionalHeaderValueByType[Origin](()) { originHeader =>
      val responseHeaders = List[HttpHeader](tracingHeader,
                                             allowOrigin(originHeader),
                                             `Access-Control-Allow-Headers`(AllowedHeaders: _*),
                                             `Access-Control-Expose-Headers`(AllowedHeaders: _*))

      respondWithHeaders(responseHeaders) {
        complete(HttpResponse(statusCode, entity = message))
      }
    }
  }
}
