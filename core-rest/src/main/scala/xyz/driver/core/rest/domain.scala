package xyz.driver.core.rest

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, ResponseEntity}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer

import scala.concurrent.Future

trait Service

object Service

trait HttpClient {
  def makeRequest(request: HttpRequest): Future[HttpResponse]
}

trait ServiceTransport {

  def sendRequestGetResponse(context: ServiceRequestContext)(requestStub: HttpRequest): Future[HttpResponse]

  def sendRequest(context: ServiceRequestContext)(requestStub: HttpRequest)(
      implicit mat: Materializer): Future[Unmarshal[ResponseEntity]]
}

sealed trait SortingOrder
object SortingOrder {
  case object Asc  extends SortingOrder
  case object Desc extends SortingOrder
}

final case class SortingField(name: String, sortingOrder: SortingOrder)
final case class Sorting(sortingFields: Seq[SortingField])

final case class Pagination(pageSize: Int, pageNumber: Int) {
  require(pageSize > 0, "Page size must be greater than zero")
  require(pageNumber > 0, "Page number must be greater than zero")

  def offset: Int = pageSize * (pageNumber - 1)
}

final case class ListResponse[+T](items: Seq[T], meta: ListResponse.Meta)

object ListResponse {

  def apply[T](items: Seq[T], size: Int, pagination: Option[Pagination]): ListResponse[T] =
    ListResponse(
      items = items,
      meta = ListResponse.Meta(size, pagination.fold(1)(_.pageNumber), pagination.fold(size)(_.pageSize)))

  final case class Meta(itemsCount: Int, pageNumber: Int, pageSize: Int)

  object Meta {
    def apply(itemsCount: Int, pagination: Pagination): Meta =
      Meta(itemsCount, pagination.pageNumber, pagination.pageSize)
  }

}
