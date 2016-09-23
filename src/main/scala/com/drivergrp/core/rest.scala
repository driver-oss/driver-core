package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.drivergrp.core.auth.AuthToken
import com.drivergrp.core.crypto.Crypto
import com.drivergrp.core.logging.Logger
import com.drivergrp.core.stats.Stats
import com.drivergrp.core.time.TimeRange
import com.drivergrp.core.time.provider.TimeProvider
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.{Failure => _, Success => _}

object rest {

  trait Service

  trait ServiceTransport {

    def sendRequest(authToken: AuthToken)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]]
  }

  trait ServiceDiscovery {

    def discover[T <: Service](serviceName: Name[Service]): T
  }

  class HttpRestServiceTransport(actorSystem: ActorSystem, executionContext: ExecutionContext,
                                 crypto: Crypto, log: Logger, stats: Stats, time: TimeProvider) extends ServiceTransport {

    protected implicit val materializer = ActorMaterializer()(actorSystem)
    protected implicit val execution = executionContext

    def sendRequest(authToken: AuthToken)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]] = {

      val requestTime = time.currentTime()
      val encryptionFlow = Flow[ByteString] map { bytes =>
        ByteString(crypto.encrypt(crypto.keyForToken(authToken))(bytes.toArray))
      }
      val decryptionFlow = Flow[ByteString] map { bytes =>
        ByteString(crypto.decrypt(crypto.keyForToken(authToken))(bytes.toArray))
      }

      val request = requestStub
        .withEntity(requestStub.entity.transformDataBytes(encryptionFlow))
        .withHeaders(
          RawHeader(auth.directives.AuthenticationTokenHeader, s"Macaroon ${authToken.value.value}"))

      log.audit(s"Sending to ${request.uri} request $request")

      Console.print("(((((((((((")

      val responseEntity = Http()(actorSystem).singleRequest(request)(materializer) map { response =>
        if(response.status.isFailure() && response.status != StatusCodes.NotFound) {
          Console.print(")))))))))")
          throw new Exception("Http status is failure " + response.status)
        } else {
          Console.print("OOOOOOOOOOOO")
          Unmarshal(response.entity.transformDataBytes(decryptionFlow))
        }
      }

      responseEntity.onComplete {
        case Success(r) =>
          Console.print("1111111111111")
          val responseTime = time.currentTime()
          log.audit(s"Response from ${request.uri} to request $requestStub is successful")
          stats.recordStats(Seq("request", request.uri.toString, "success"), TimeRange(requestTime, responseTime), 1)

        case Failure(t: Throwable) =>
          Console.print("2222222222222")
          val responseTime = time.currentTime()
          log.audit(s"Failed to receive response from ${request.uri} to request $requestStub")
          log.error(s"Failed to receive response from ${request.uri} to request $requestStub", t)
          stats.recordStats(Seq("request", request.uri.toString, "fail"), TimeRange(requestTime, responseTime), 1)
      } (executionContext)

      responseEntity
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
