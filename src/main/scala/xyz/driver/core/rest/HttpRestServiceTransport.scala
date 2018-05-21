package xyz.driver.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.TcpIdleTimeoutException
import com.typesafe.scalalogging.Logger
import org.slf4j.MDC
import xyz.driver.core.Name
import xyz.driver.core.rest.errors.{ExternalServiceException, ExternalServiceTimeoutException}
import xyz.driver.core.time.provider.TimeProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class HttpRestServiceTransport(
    applicationName: Name[App],
    applicationVersion: String,
    actorSystem: ActorSystem,
    executionContext: ExecutionContext,
    log: Logger,
    time: TimeProvider)
    extends ServiceTransport {

  protected implicit val execution: ExecutionContext = executionContext

  protected val httpClient: HttpClient = new SingleRequestHttpClient(applicationName, applicationVersion, actorSystem)

  def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse] = {

    val requestTime = time.currentTime()

    val request = requestStub
      .withHeaders(context.contextHeaders.toSeq.map {
        case (ContextHeaders.TrackingIdHeader, _) =>
          RawHeader(ContextHeaders.TrackingIdHeader, context.trackingId)
        case (ContextHeaders.StacktraceHeader, _) =>
          RawHeader(
            ContextHeaders.StacktraceHeader,
            Option(MDC.get("stack"))
              .orElse(context.contextHeaders.get(ContextHeaders.StacktraceHeader))
              .getOrElse(""))
        case (header, headerValue) => RawHeader(header, headerValue)
      }: _*)

    log.debug(s"Sending request to ${request.method} ${request.uri}")

    val response = httpClient.makeRequest(request)

    response.onComplete {
      case Success(r) =>
        val responseLatency = requestTime.durationTo(time.currentTime())
        log.debug(s"Response from ${request.uri} to request $requestStub is successful in $responseLatency ms: $r")

      case Failure(t: Throwable) =>
        val responseLatency = requestTime.durationTo(time.currentTime())
        log.warn(s"Failed to receive response from ${request.method} ${request.uri} in $responseLatency ms", t)
    }(executionContext)

    response.recoverWith {
      case _: TcpIdleTimeoutException =>
        val serviceCalled = s"${requestStub.method} ${requestStub.uri}"
        Future.failed(ExternalServiceTimeoutException(serviceCalled))
      case t: Throwable => Future.failed(t)
    }
  }

  def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest)(
      implicit mat: Materializer): Future[Unmarshal[ResponseEntity]] = {

    sendRequestGetResponse(context)(requestStub) flatMap { response =>
      if (response.status == StatusCodes.NotFound) {
        Future.successful(Unmarshal(HttpEntity.Empty: ResponseEntity))
      } else if (response.status.isFailure()) {
        val serviceCalled = s"${requestStub.method} ${requestStub.uri}"
        Unmarshal(response.entity).to[String] flatMap { errorString =>
          import spray.json._
          import xyz.driver.core.json._
          val serviceException = util.Try(serviceExceptionFormat.read(errorString.parseJson)).toOption
          Future.failed(ExternalServiceException(serviceCalled, errorString, serviceException))
        }
      } else {
        Future.successful(Unmarshal(response.entity))
      }
    }
  }
}
