package xyz.driver.core.rest

import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server._
import spray.json._
import xyz.driver.core.Id

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scalaz.syntax.equal._
import scalaz.Scalaz.stringInstance

trait PatchSupport extends Directives with SprayJsonSupport {
  protected val MergePatchPlusJson: String = "merge-patch+json"

  trait PatchRetrievable[T] {
    def apply(id: Id[T])(implicit ctx: ServiceRequestContext): Future[Option[T]]
  }

  object PatchRetrievable {
    def apply[T](retriever: (Id[T] => (ServiceRequestContext => Future[Option[T]]))): PatchRetrievable[T] =
      new PatchRetrievable[T] {
        override def apply(id: Id[T])(implicit ctx: ServiceRequestContext): Future[Option[T]] = retriever(id)(ctx)
      }
  }

  protected def mergeObjects(oldObj: JsObject, newObj: JsObject, maxLevels: Option[Int] = None): JsObject = {
    JsObject(oldObj.fields.map({
      case (key, oldValue) =>
        val newValue = newObj.fields.get(key).fold(oldValue)(mergeJsValues(oldValue, _, maxLevels.map(_ - 1)))
        key -> newValue
    })(collection.breakOut): _*)
  }

  protected def mergeJsValues(oldValue: JsValue, newValue: JsValue, maxLevels: Option[Int] = None): JsValue = {
    def mergeError(typ: String): Nothing =
      deserializationError(s"Expected $typ value, got $newValue")

    if (maxLevels.exists(_ < 0)) oldValue
    else {
      (oldValue, newValue) match {
        case (_: JsString, newString @ (JsString(_) | JsNull)) => newString
        case (_: JsString, _)                                  => mergeError("string")
        case (_: JsNumber, newNumber @ (JsNumber(_) | JsNull)) => newNumber
        case (_: JsNumber, _)                                  => mergeError("number")
        case (_: JsBoolean, newBool @ (JsBoolean(_) | JsNull)) => newBool
        case (_: JsBoolean, _)                                 => mergeError("boolean")
        case (_: JsArray, newArr @ (JsArray(_) | JsNull))      => newArr
        case (_: JsArray, _)                                   => mergeError("array")
        case (oldObj: JsObject, newObj: JsObject)              => mergeObjects(oldObj, newObj)
        case (_: JsObject, JsNull)                             => JsNull
        case (_: JsObject, _)                                  => mergeError("object")
        case (JsNull, _)                                       => newValue
      }
    }
  }

  def rejectNonMergePatchContentType: Directive0 = Directive { inner => requestCtx =>
    val contentType = requestCtx.request.header[akka.http.scaladsl.model.headers.`Content-Type`]
    val isCorrectContentType =
      contentType.map(_.contentType.mediaType).exists(mt => mt.isApplication && mt.subType === MergePatchPlusJson)
    if (!isCorrectContentType) {
      reject(
        Rejections.malformedRequestContent(
          s"Request Content-Type must be application/$MergePatchPlusJson for PATCH requests",
          new RuntimeException))(requestCtx)
    } else inner(())(requestCtx)
  }

  def as[T](
      implicit patchable: PatchRetrievable[T],
      jsonFormat: RootJsonFormat[T]): (PatchRetrievable[T], RootJsonFormat[T]) =
    (patchable, jsonFormat)

  def patch[T](patchable: (PatchRetrievable[T], RootJsonFormat[T]), id: Id[T]): Directive1[T] = Directive {
    inner => requestCtx =>
      import requestCtx.executionContext
      val retriever                              = patchable._1
      implicit val jsonFormat: RootJsonFormat[T] = patchable._2
      Directives.patch {
        rejectNonMergePatchContentType {
          entity(as[JsValue]) { newValue =>
            serviceContext { implicit ctx =>
              onSuccess(retriever(id).map(_.map(_.toJson))) {
                case Some(oldValue) =>
                  val mergedObj = mergeJsValues(oldValue, newValue)
                  Try(mergedObj.convertTo[T])
                    .transform[Route](
                      mergedT => scala.util.Success(inner(Tuple1(mergedT))), {
                        case jsonException: DeserializationException =>
                          Success(reject(Rejections.malformedRequestContent(jsonException.getMessage, jsonException)))
                        case t => Failure(t)
                      }
                    )
                    .get // intentionally re-throw all other errors
                case None =>
                  reject()
              }
            }
          }
        }
      }(requestCtx)
  }
}

object PatchSupport extends PatchSupport
