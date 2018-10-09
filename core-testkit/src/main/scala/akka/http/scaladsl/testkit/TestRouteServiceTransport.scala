package akka.http.scaladsl.testkit

import akka.actor.ActorSystem
import akka.http.javadsl.testkit.DefaultHostInfo
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Host, RawHeader}
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import org.slf4j.MDC
import xyz.driver.core.rest.errors.ExternalServiceException
import xyz.driver.core.rest.{ContextHeaders, ServiceRequestContext, ServiceTransport}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class TestRouteServiceTransport(route: Route, routeTimeout: FiniteDuration = 10 seconds)(
    implicit executor: ExecutionContextExecutor,
    system: ActorSystem)
    extends ServiceTransport with RouteTestResultComponent {

  def defaultHost: DefaultHostInfo     = DefaultHostInfo(Host("example.com"), securedConnection = false)
  def routingLog: RoutingLog           = RoutingLog(system.log)
  def routingSettings: RoutingSettings = RoutingSettings.apply(system)
  implicit val materializer            = ActorMaterializer()

  override def sendRequestGetResponse(context: ServiceRequestContext)(
      requestStub: HttpRequest): Future[HttpResponse] = {

    val request = requestStub
      .withHeaders(requestStub.headers ++ context.contextHeaders.toSeq.map {
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

    // Code below is forked from `akka.http.scaladsl.testkit.RouteTest.TildeArrow.injectIntoRoute`,
    // because it doesn't allow to call just this code outside of the DSL for testkit tests
    val routeTestResult = new RouteTestResult(routeTimeout)

    val effectiveRequest = request

    val log                    = routingLog.requestLog(effectiveRequest)
    val ctx                    = new RequestContextImpl(effectiveRequest, log, routingSettings)(executor, materializer)
    val sealedExceptionHandler = ExceptionHandler.default(routingSettings)
    val semiSealedRoute        = Directives.handleExceptions(sealedExceptionHandler)(route)
    val deferrableRouteResult  = semiSealedRoute(ctx)

    deferrableRouteResult.map(r => { routeTestResult.handleResult(r); routeTestResult.response })
  }

  override def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest)(
      implicit mat: Materializer): Future[Unmarshal[ResponseEntity]] = {
    sendRequestGetResponse(context)(requestStub) flatMap { response =>
      if (response.status == StatusCodes.NotFound) {
        Future.successful(Unmarshal(HttpEntity.Empty: ResponseEntity))
      } else if (response.status.isFailure()) {
        val serviceCalled = s"${requestStub.method} ${requestStub.uri}"
        Unmarshal(response.entity).to[String] flatMap { errorString =>
          import spray.json._
          import xyz.driver.core.json._
          val serviceException = scala.util.Try(serviceExceptionFormat.read(errorString.parseJson)).toOption
          Future.failed(ExternalServiceException(serviceCalled, errorString, serviceException))
        }
      } else {
        Future.successful(Unmarshal(response.entity))
      }
    }
  }

  override def failTest(msg: String): Nothing = throw new Exception(msg)
}
