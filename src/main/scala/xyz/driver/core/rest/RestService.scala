package xyz.driver.core.rest

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}
import scalaz.syntax.equal._
import scalaz.Scalaz.stringInstance

trait RestService extends Service {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json._

  protected implicit val exec: ExecutionContext
  protected implicit val materializer: Materializer

  implicit class ResponseEntityFoldable(entity: Unmarshal[ResponseEntity]) {
    def fold[T](default: => T)(implicit um: Unmarshaller[ResponseEntity, T]): Future[T] =
      if (entity.value.isKnownEmpty()) Future.successful[T](default) else entity.to[T]
  }

  protected def unitResponse(request: Future[Unmarshal[ResponseEntity]]): OptionT[Future, Unit] =
    OptionT[Future, Unit](request.flatMap(_.to[String]).map(_ => Option(())))

  protected def optionalResponse[T](request: Future[Unmarshal[ResponseEntity]])(
      implicit um: Unmarshaller[ResponseEntity, Option[T]]): OptionT[Future, T] =
    OptionT[Future, T](request.flatMap(_.fold(Option.empty[T])))

  protected def listResponse[T](request: Future[Unmarshal[ResponseEntity]])(
      implicit um: Unmarshaller[ResponseEntity, List[T]]): ListT[Future, T] =
    ListT[Future, T](request.flatMap(_.fold(List.empty[T])))

  protected def jsonEntity(json: JsValue): RequestEntity =
    HttpEntity(ContentTypes.`application/json`, json.compactPrint)

  protected def mergePatchJsonEntity(json: JsValue): RequestEntity =
    HttpEntity(PatchDirectives.`application/merge-patch+json`, json.compactPrint)

  protected def get(baseUri: Uri, path: String, query: Seq[(String, String)] = Seq.empty) =
    HttpRequest(HttpMethods.GET, endpointUri(baseUri, path, query))

  protected def post(baseUri: Uri, path: String, httpEntity: RequestEntity) =
    HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = httpEntity)

  protected def postJson(baseUri: Uri, path: String, json: JsValue) =
    HttpRequest(HttpMethods.POST, endpointUri(baseUri, path), entity = jsonEntity(json))

  protected def put(baseUri: Uri, path: String, httpEntity: RequestEntity) =
    HttpRequest(HttpMethods.PUT, endpointUri(baseUri, path), entity = httpEntity)

  protected def putJson(baseUri: Uri, path: String, json: JsValue) =
    HttpRequest(HttpMethods.PUT, endpointUri(baseUri, path), entity = jsonEntity(json))

  protected def patch(baseUri: Uri, path: String, httpEntity: RequestEntity) =
    HttpRequest(HttpMethods.PATCH, endpointUri(baseUri, path), entity = httpEntity)

  protected def patchJson(baseUri: Uri, path: String, json: JsValue) =
    HttpRequest(HttpMethods.PATCH, endpointUri(baseUri, path), entity = jsonEntity(json))

  protected def mergePatchJson(baseUri: Uri, path: String, json: JsValue) =
    HttpRequest(HttpMethods.PATCH, endpointUri(baseUri, path), entity = mergePatchJsonEntity(json))

  protected def delete(baseUri: Uri, path: String, query: Seq[(String, String)] = Seq.empty) =
    HttpRequest(HttpMethods.DELETE, endpointUri(baseUri, path, query))

  protected def endpointUri(baseUri: Uri, path: String): Uri =
    baseUri.withPath(Uri.Path(path))

  protected def endpointUri(baseUri: Uri, path: String, query: Seq[(String, String)]): Uri =
    baseUri.withPath(Uri.Path(path)).withQuery(Uri.Query(query: _*))

  protected def responseToListResponse[T: JsonFormat](pagination: Option[Pagination])(
      response: HttpResponse): Future[ListResponse[T]] = {
    import DefaultJsonProtocol._
    val resourceCount = response.headers
      .find(_.name() === ContextHeaders.ResourceCount)
      .map(_.value().toInt)
      .getOrElse(0)
    val meta = ListResponse.Meta(resourceCount, pagination.getOrElse(Pagination(resourceCount, 1)))
    Unmarshal(response.entity).to[List[T]].map(ListResponse(_, meta))
  }

  protected def responseToListResponse[T: JsonFormat](pagination: Pagination)(
      response: HttpResponse): Future[ListResponse[T]] = responseToListResponse(Some(pagination))(response)
}
