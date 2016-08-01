package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.{Marshal, Marshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.{ByteString, Timeout}
import com.drivergrp.core.crypto.{AuthToken, Crypto}
import com.drivergrp.core.logging.Logger
import com.drivergrp.core.stats.Stats
import com.drivergrp.core.time.TimeRange
import com.drivergrp.core.time.provider.TimeProvider
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}
import scalaz.{Failure => _, Success => _}
import scalaz.Scalaz._

object rest {

  final case class ServiceVersion(majorVersion: Int, minorVersion: Int) {
    def isCompatible(otherVersion: ServiceVersion) =
      this.majorVersion === otherVersion.majorVersion
  }

  trait Service {

    def sendRequest[I,O](authToken: AuthToken)(requestInput: I)
                        (implicit marshaller: Marshaller[I, RequestEntity],
                                  unmarshaller: Unmarshaller[ResponseEntity, O]): Future[O]
  }

  trait ServiceDiscovery {

    def discover(serviceName: Name[Service], version: ServiceVersion): Service
  }

  class HttpRestService(method: HttpMethod, uri: Uri, version: ServiceVersion,
                        actorSystem: ActorSystem, executionContext: ExecutionContext,
                        crypto: Crypto, log: Logger, stats: Stats, time: TimeProvider) extends Service {

    protected implicit val materializer = ActorMaterializer()(actorSystem)
    protected implicit val execution = executionContext
    protected implicit val timeout = Timeout(5 seconds)

    def sendRequest[I,O](authToken: AuthToken)(requestInput: I)
                        (implicit marshaller: Marshaller[I, RequestEntity],
                                  unmarshaller: Unmarshaller[ResponseEntity, O]): Future[O] = {

      val requestTime = time.currentTime()
      val encryptionFlow = Flow[ByteString] map { bytes =>
        ByteString(crypto.encrypt(crypto.keyForToken(authToken))(bytes.toArray))
      }
      val decryptionFlow = Flow[ByteString] map { bytes =>
        ByteString(crypto.decrypt(crypto.keyForToken(authToken))(bytes.toArray))
      }

      val response: Future[O] = for {
        requestData: RequestEntity <- Marshal(requestInput).to[RequestEntity](marshaller, executionContext)
        encryptedMessage = requestData.transformDataBytes(encryptionFlow)
        request: HttpRequest = buildRequest(authToken, requestData)
        _ = log.audit(s"Sending to ${request.uri} request $request")
        response <- Http()(actorSystem).singleRequest(request)(materializer)
        decryptedResponse = requestData.transformDataBytes(decryptionFlow)
        responseEntity <- Unmarshal(decryptedResponse).to[O](unmarshaller, executionContext, materializer)
      } yield {
        responseEntity
      }

      response.onComplete {
        case Success(r) =>
          val responseTime = time.currentTime()
          log.audit(s"Response from $uri to request $requestInput is successful")
          stats.recordStats(Seq("request", uri.toString, "success"), TimeRange(requestTime, responseTime), 1)

        case Failure(t: Throwable) =>
          val responseTime = time.currentTime()
          log.audit(s"Failed to receive response from $uri of version $version to request $requestInput")
          log.error(s"Failed to receive response from $uri of version $version to request $requestInput", t)
          stats.recordStats(Seq("request", uri.toString, "fail"), TimeRange(requestTime, responseTime), 1)
      } (executionContext)

      response
    }

    private def buildRequest(authToken: AuthToken, requestData: RequestEntity): HttpRequest = {

      HttpRequest(
        method, uri,
        headers = Vector(
          RawHeader("WWW-Authenticate", s"Macaroon ${authToken.value.value}"),
          RawHeader("Api-Version", version.majorVersion + "." + version.minorVersion)
        ),
        entity = requestData)
    }
  }

  import scala.reflect.runtime.universe._

  class Swagger(override val host: String,
                override val actorSystem: ActorSystem,
                override val apiTypes: Seq[Type],
                val config: Config) extends SwaggerHttpService with HasActorSystem {

    val materializer = ActorMaterializer()(actorSystem)

    override val basePath = config.getString("swagger.basePath")
    override val apiDocsPath = config.getString("swagger.docsPath")

    override val info = Info(
      config.getString("swagger.apiInfo.description"),
      config.getString("swagger.apiVersion"),
      config.getString("swagger.apiInfo.title"),
      config.getString("swagger.apiInfo.termsOfServiceUrl"),
      contact = Some(Contact(
        config.getString("swagger.apiInfo.contact.name"),
        config.getString("swagger.apiInfo.contact.url"),
        config.getString("swagger.apiInfo.contact.email")
      )),
      license = Some(License(
        config.getString("swagger.apiInfo.license"),
        config.getString("swagger.apiInfo.licenseUrl")
      )),
      vendorExtensions = Map.empty[String, AnyRef])

    def swaggerUI = get {
      pathPrefix("") {
        pathEndOrSingleSlash {
          getFromResource("swagger-ui/index.html")
        }
      } ~ getFromResourceDirectory("swagger-ui")
    }
  }
}
