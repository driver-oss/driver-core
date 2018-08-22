package xyz.driver.core
package rest
package directives

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Route, Directives => AkkaDirectives}

/** Directives to handle Cross-Origin Resource Sharing (CORS). */
trait CorsDirectives extends AkkaDirectives {

  /** Route handler that injects Cross-Origin Resource Sharing (CORS) headers depending on the request
    * origin.
    *
    * In a microservice environment, it can be difficult to know in advance the exact origin
    * from which requests may be issued [1]. For example, the request may come from a web page served from
    * any of the services, on any namespace or from other documentation sites. In general, only a set
    * of domain suffixes can be assumed to be known in advance. Unfortunately however, browsers that
    * implement CORS require exact specification of allowed origins, including full host name and scheme,
    * in order to send credentials and headers with requests to other origins.
    *
    * This route wrapper provides a simple way alleviate CORS' exact allowed-origin requirement by
    * dynamically echoing the origin as an allowed origin if and only if its domain is whitelisted.
    *
    * Note that the simplicity of this implementation comes with two notable drawbacks:
    *
    *   - All OPTION requests are "hijacked" and will not be passed to the inner route of this wrapper.
    *
    *   - Allowed methods and headers can not be customized on a per-request basis. All standard
    *   HTTP methods are allowed, and allowed headers are specified for all inner routes.
    *
    * This handler is not suited for cases where more fine-grained control of responses is required.
    *
    * [1] Assuming browsers communicate directly with the services and that requests aren't proxied through
    * a common gateway.
    *
    * @param allowedSuffixes The set of domain suffixes (e.g. internal.example.org, example.org) of allowed
    *                        origins.
    * @param allowedHeaders Header names that will be set in `Access-Control-Allow-Headers`.
    * @param inner Route into which CORS headers will be injected.
    */
  def cors(allowedSuffixes: Set[String], allowedHeaders: Seq[String])(inner: Route): Route = {
    optionalHeaderValueByType[Origin](()) { maybeOrigin =>
      val allowedOrigins: HttpOriginRange = maybeOrigin match {
        // Note that this is not a security issue: the client will never send credentials if the allowed
        // origin is set to *. This case allows us to deal with clients that do not send an origin header.
        case None => HttpOriginRange.*
        case Some(requestOrigin) =>
          val allowedOrigin = requestOrigin.origins.find(origin =>
            allowedSuffixes.exists(allowed => origin.host.host.address endsWith allowed))
          allowedOrigin.map(HttpOriginRange(_)).getOrElse(HttpOriginRange.*)
      }

      respondWithHeaders(
        `Access-Control-Allow-Origin`.forRange(allowedOrigins),
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`(allowedHeaders: _*),
        `Access-Control-Expose-Headers`(allowedHeaders: _*)
      ) {
        options { // options is used during preflight check
          complete(
            HttpResponse(StatusCodes.OK)
              .withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE, PATCH, TRACE)))
        } ~ inner // in case of non-preflight check we don't do anything special
      }
    }
  }

}

object CorsDirectives extends CorsDirectives
