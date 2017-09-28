package xyz.driver.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.Logger
import org.slf4j.MDC
import xyz.driver.core.Name
import xyz.driver.core.time.provider.TimeProvider

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class HttpRestServiceTransport(applicationName: Name[App],
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
          RawHeader(ContextHeaders.StacktraceHeader,
                    Option(MDC.get("stack"))
                      .orElse(context.contextHeaders.get(ContextHeaders.StacktraceHeader))
                      .getOrElse(""))
        case (header, headerValue) => RawHeader(header, headerValue)
      }: _*)

    log.info(s"Sending request to ${request.method} ${request.uri}")

    val response = httpClient.makeRequest(request)

    response.onComplete {
      case Success(r) =>
        val responseLatency = requestTime.durationTo(time.currentTime())
        log.info(s"Response from ${request.uri} to request $requestStub is successful in $responseLatency ms: $r")

      case Failure(t: Throwable) =>
        val responseLatency = requestTime.durationTo(time.currentTime())
        log.info(s"Failed to receive response from ${request.method} ${request.uri} in $responseLatency ms", t)
        log.warn(s"Failed to receive response from ${request.method} ${request.uri} in $responseLatency ms", t)
    }(executionContext)

    response
  }

  def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]] = {

    sendRequestGetResponse(context)(requestStub) map { response =>
      if (response.status == StatusCodes.NotFound) {
        Unmarshal(HttpEntity.Empty: ResponseEntity)
      } else if (response.status.isFailure()) {
        throw new Exception(s"Http status is failure ${response.status} for ${requestStub.method} ${requestStub.uri}")
      } else {
        Unmarshal(response.entity)
      }
    }
  }
}
