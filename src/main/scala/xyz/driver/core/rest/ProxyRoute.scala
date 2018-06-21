package xyz.driver.core.rest

import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import com.typesafe.config.Config
import xyz.driver.core.Name

import scala.concurrent.ExecutionContext

trait ProxyRoute extends DriverRoute {
  implicit val executionContext: ExecutionContext
  val config: Config
  val httpClient: HttpClient

  protected def proxyToService(serviceName: Name[Service]): Route = { ctx: RequestContext =>
    val httpScheme = config.getString(s"services.${serviceName.value}.httpScheme")
    val baseUrl    = config.getString(s"services.${serviceName.value}.baseUrl")

    val originalUri     = ctx.request.uri
    val originalRequest = ctx.request

    val newUri     = originalUri.withScheme(httpScheme).withHost(baseUrl)
    val newRequest = originalRequest.withUri(newUri)

    httpClient.makeRequest(newRequest).map(RouteResult.Complete)
  }
}
