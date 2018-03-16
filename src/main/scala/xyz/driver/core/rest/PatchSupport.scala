package xyz.driver.core.rest

import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directive, Directive1, Directives, Route}
import spray.json._
import xyz.driver.core.Id

import scala.concurrent.Future

trait PatchSupport extends Directives with SprayJsonSupport {

  trait PatchRetrievable[T] {
    def apply(id: Id[T]): Future[Option[T]]
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
        entity(as[JsValue]) { newValue =>
          onSuccess(retriever(id).map(_.map(_.toJson))) {
            case Some(oldValue) =>
              val mergedObj = mergeJsValues(oldValue, newValue)
              util
                .Try(mergedObj.convertTo[T])
                .transform[Route](
                  mergedT => util.Success(inner(Tuple1(mergedT))), {
                    case jsonException: DeserializationException =>
                      util.Success(reject(Rejections.malformedRequestContent(jsonException.getMessage, jsonException)))
                    case t => util.Failure(t)
                  }
                )
                .get // intentionally re-throw all other errors
            case None =>
              reject()
          }
        }
      }(requestCtx)
  }
}

object PatchSupport extends PatchSupport
