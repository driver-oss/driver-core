package xyz.driver.core.rest

import akka.http.javadsl.server.Rejections
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypeRange, HttpCharsets, MediaType}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait PatchDirectives extends Directives with SprayJsonSupport {

  /** Media type for patches to JSON values, as specified in [[https://tools.ietf.org/html/rfc7396 RFC 7396]]. */
  val `application/merge-patch+json`: MediaType.WithFixedCharset =
    MediaType.applicationWithFixedCharset("merge-patch+json", HttpCharsets.`UTF-8`)

  /** Wraps a JSON value that represents a patch.
    * The patch must given in the format specified in [[https://tools.ietf.org/html/rfc7396 RFC 7396]]. */
  case class PatchValue(value: JsValue) {

    /** Applies this patch to a given original JSON value. In other words, merges the original with this "diff". */
    def applyTo(original: JsValue): JsValue = mergeJsValues(original, value)
  }

  /** Witness that the given patch may be applied to an original domain value.
    * @tparam A type of the domain value
    * @param patch the patch that may be applied to a domain value
    * @param format a JSON format that enables serialization and deserialization of a domain value */
  case class Patchable[A](patch: PatchValue, format: RootJsonFormat[A]) {

    /** Applies the patch to a given domain object. The result will be a combination
      * of the original value, updates with the fields specified in this witness' patch. */
    def applyTo(original: A): A = {
      val serialized   = format.write(original)
      val merged       = patch.applyTo(serialized)
      val deserialized = format.read(merged)
      deserialized
    }
  }

  implicit def patchValueUnmarshaller: FromEntityUnmarshaller[PatchValue] =
    Unmarshaller.byteStringUnmarshaller
      .andThen(sprayJsValueByteStringUnmarshaller)
      .forContentTypes(ContentTypeRange(`application/merge-patch+json`))
      .map(js => PatchValue(js))

  implicit def patchableUnmarshaller[A](
      implicit patchUnmarshaller: FromEntityUnmarshaller[PatchValue],
      format: RootJsonFormat[A]): FromEntityUnmarshaller[Patchable[A]] = {
    patchUnmarshaller.map(patch => Patchable[A](patch, format))
  }

  protected def mergeObjects(oldObj: JsObject, newObj: JsObject, maxLevels: Option[Int] = None): JsObject = {
    JsObject((oldObj.fields.keys ++ newObj.fields.keys).map({ key =>
      val oldValue = oldObj.fields.getOrElse(key, JsNull)
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

  def mergePatch[T](patchable: Patchable[T], retrieve: => Future[Option[T]]): Directive1[T] =
    Directive { inner => requestCtx =>
      onSuccess(retrieve)({
        case Some(oldT) =>
          Try(patchable.applyTo(oldT))
            .transform[Route](
              mergedT => scala.util.Success(inner(Tuple1(mergedT))), {
                case jsonException: DeserializationException =>
                  Success(reject(Rejections.malformedRequestContent(jsonException.getMessage, jsonException)))
                case t => Failure(t)
              }
            )
            .get // intentionally re-throw all other errors
        case None => reject()
      })(requestCtx)
    }
}

object PatchDirectives extends PatchDirectives
