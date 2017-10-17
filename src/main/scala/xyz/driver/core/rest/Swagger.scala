package xyz.driver.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream._
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config
import io.swagger.models.Scheme

import scala.reflect.runtime.universe._

class Swagger(override val host: String,
              override val scheme: Scheme,
              version: String,
              override val actorSystem: ActorSystem,
              override val apiTypes: Seq[Type],
              val config: Config)
    extends SwaggerHttpService with HasActorSystem {

  val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  override val basePath: String    = config.getString("swagger.basePath")
  override val apiDocsPath: String = config.getString("swagger.docsPath")

  override val info = Info(
    config.getString("swagger.apiInfo.description"),
    version,
    config.getString("swagger.apiInfo.title"),
    config.getString("swagger.apiInfo.termsOfServiceUrl"),
    contact = Some(
      Contact(
        config.getString("swagger.apiInfo.contact.name"),
        config.getString("swagger.apiInfo.contact.url"),
        config.getString("swagger.apiInfo.contact.email")
      )),
    license = Some(
      License(
        config.getString("swagger.apiInfo.license"),
        config.getString("swagger.apiInfo.licenseUrl")
      )),
    vendorExtensions = Map.empty[String, AnyRef]
  )

  def swaggerUI: Route = get {
    pathPrefix("") {
      pathEndOrSingleSlash {
        getFromResource("swagger-ui/index.html")
      }
    } ~ getFromResourceDirectory("swagger-ui")
  }
}
