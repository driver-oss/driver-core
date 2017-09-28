package xyz.driver.core.rest

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.Future

trait ServiceTransport {

  def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse]

  def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest): Future[Unmarshal[ResponseEntity]]
}
