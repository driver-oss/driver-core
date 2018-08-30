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
      "service.version" -> version.getOrElse("<unknown>"),
      // open tracing semantic tags
      "span.kind"     -> "server",
      "service"       -> name,
      "http.url"      -> ctx.request.uri.toString,
      "http.method"   -> ctx.request.method.value,
      "peer.hostname" -> ctx.request.uri.authority.host.toString,
      // google's tracing console provides extra search features if we define these tags
      "/http/path"       -> ctx.request.uri.path.toString,
      "/http/method"     -> ctx.request.method.value.toString,
      "/http/url"        -> ctx.request.uri.toString,
      "/http/user_agent" -> ctx.request.header[`User-Agent`].map(_.value).getOrElse("<unknown>")
    )
    val parent = ctx.request.header[Traceparent].map { header =>
      header.spanContext -> CausalRelation.Child
    }
    reporter
      .traceWithOptionalParentAsync(s"http_handle_rpc", tags, parent) { spanContext =>
        val header     = Traceparent(spanContext)
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
