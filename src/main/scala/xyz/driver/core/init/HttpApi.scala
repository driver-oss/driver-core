package xyz.driver.core
package init

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{RequestContext, Route, RouteConcatenation}
import spray.json.DefaultJsonProtocol._
import spray.json._
import xyz.driver.core.rest.Swagger
import xyz.driver.core.rest.directives.Directives
import akka.http.scaladsl.model.headers._
import xyz.driver.core.reporting.Reporter.CausalRelation
import xyz.driver.core.reporting.SpanContext
import xyz.driver.core.rest.headers.Traceparent

import scala.collection.JavaConverters._

/** Mixin trait that provides some well-known HTTP endpoints, diagnostic header injection and forwarding,
  * and exposes an application-specific route that must be implemented by services.
  * @see ProtobufApi
  */
trait HttpApi extends CloudServices with Directives with SprayJsonSupport { self =>

  /** Route that handles the application's business logic.
    * @group hooks
    */
  def applicationRoute: Route

  /** Classes with Swagger annotations.
    * @group hooks
    */
  def swaggerRouteClasses: Set[Class[_]]

  private val healthRoute = path("health") {
    complete(Map("status" -> "good").toJson)
  }

  private val versionRoute = path("version") {
    complete(Map("name" -> self.name.toJson, "version" -> self.version.toJson).toJson)
  }

  private lazy val swaggerRoute = {
    val generator = new Swagger(
      "",
      "https" :: "http" :: Nil,
      self.version.getOrElse("<unknown>"),
      swaggerRouteClasses,
      config,
      reporter
    )
    generator.routes ~ generator.swaggerUI
  }

  private def cors(inner: Route): Route =
    cors(
      config.getStringList("application.cors.allowedOrigins").asScala.toSet,
      xyz.driver.core.rest.AllowedHeaders
    )(inner)

  private def traced(inner: Route): Route = (ctx: RequestContext) => {
    val tags = Map(
      "service_name"    -> name,
      "service_version" -> version.getOrElse("<unknown>"),
      "http_user_agent" -> ctx.request.header[`User-Agent`].map(_.value).getOrElse("<unknown>"),
      "http_uri"        -> ctx.request.uri.toString,
      "http_path"       -> ctx.request.uri.path.toString
    )
    val parent = ctx.request.header[Traceparent].map { p =>
      SpanContext(p.traceId, p.spanId) -> CausalRelation.Child
    }
    reporter.traceWithOptionalParentAsync("handle_service_request", tags, parent) { sctx =>
      val header     = Traceparent(sctx.traceId, sctx.spanId)
      val withHeader = ctx.withRequest(ctx.request.withHeaders(header))
      inner(withHeader)
    }
  }

  /** Extended route. */
  override lazy val route: Route = traced(
    cors(
      RouteConcatenation.concat(
        healthRoute,
        versionRoute,
        swaggerRoute,
        applicationRoute
      )
    )
  )

}
