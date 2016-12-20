package xyz.driver.core

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.{PathMatcher, _}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json.{DeserializationException, JsNumber, _}
import xyz.driver.core.revision.Revision
import xyz.driver.core.time.Time
import xyz.driver.core.date.Month

import scala.reflect.runtime.universe._

object json {

  def IdInPath[T]: PathMatcher1[Id[T]] = new PathMatcher1[Id[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Id[T](segment)))
      case _                           => Unmatched
    }
  }

  implicit def idFormat[T] = new RootJsonFormat[Id[T]] {
    def write(id: Id[T]) = JsString(id.value)

    def read(value: JsValue) = value match {
      case JsString(id) => Id[T](id)
      case _            => throw DeserializationException("Id expects string")
    }
  }

  def NameInPath[T]: PathMatcher1[Name[T]] = new PathMatcher1[Name[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Name[T](segment)))
      case _                           => Unmatched
    }
  }

  implicit def nameFormat[T] = new RootJsonFormat[Name[T]] {
    def write(name: Name[T]) = JsString(name.value)

    def read(value: JsValue): Name[T] = value match {
      case JsString(name) => Name[T](name)
      case _              => throw DeserializationException("Name expects string")
    }
  }

  def TimeInPath: PathMatcher1[Time] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(Time(string.toLong))
      catch { case _: IllegalArgumentException => None }
    }

  implicit val timeFormat = new RootJsonFormat[Time] {
    def write(time: Time) = JsObject("timestamp" -> JsNumber(time.millis))

    def read(value: JsValue): Time = value match {
      case JsObject(fields) =>
        fields
          .get("timestamp")
          .flatMap {
            case JsNumber(millis) => Some(Time(millis.toLong))
            case _                => None
          }
          .getOrElse(throw DeserializationException("Time expects number"))
      case _ => throw DeserializationException("Time expects number")
    }
  }

  implicit val monthFormat = new RootJsonFormat[Month] {
    def write(month: Month) = JsNumber(month)
    def read(value: JsValue): Month = value match {
      case JsNumber(month) if 0 <= month && month <= 11 => date.tagMonth(month.toInt)
      case _                                            => throw DeserializationException("Month is a 0-based number")
    }
  }

  def RevisionInPath[T]: PathMatcher1[Revision[T]] =
    PathMatcher("""[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r) flatMap { string =>
      Some(Revision[T](string))
    }

  implicit def revisionFromStringUnmarshaller[T]: Unmarshaller[String, Revision[T]] =
    Unmarshaller.strict[String, Revision[T]](Revision[T](_))

  implicit def revisionFormat[T] = new RootJsonFormat[Revision[T]] {
    def write(revision: Revision[T]) = JsString(revision.id.toString)

    def read(value: JsValue): Revision[T] = value match {
      case JsString(revision) => Revision[T](revision)
      case _                  => throw DeserializationException("Revision expects uuid string")
    }
  }

  class EnumJsonFormat[T](mapping: (String, T)*) extends RootJsonFormat[T] {
    private val map = mapping.toMap

    override def write(value: T): JsValue = {
      map.find(_._2 == value).map(_._1) match {
        case Some(name) => JsString(name)
        case _          => serializationError(s"Value $value is not found in the mapping $map")
      }
    }

    override def read(json: JsValue): T = json match {
      case JsString(name) =>
        map.getOrElse(name, throw DeserializationException(s"Value $name is not found in the mapping $map"))
      case _ => deserializationError("Expected string as enumeration value, but got " + json)
    }
  }

  class ValueClassFormat[T: TypeTag](writeValue: T => BigDecimal, create: BigDecimal => T) extends JsonFormat[T] {
    def write(valueClass: T) = JsNumber(writeValue(valueClass))
    def read(json: JsValue): T = json match {
      case JsNumber(value) => create(value)
      case _               => deserializationError(s"Expected number as ${typeOf[T].getClass.getName}, but got " + json)
    }
  }

  class GadtJsonFormat[T: TypeTag](typeField: String,
                                   typeValue: PartialFunction[T, String],
                                   jsonFormat: PartialFunction[String, JsonFormat[_ <: T]])
      extends RootJsonFormat[T] {

    def write(value: T): JsValue = {

      val valueType = typeValue.applyOrElse(value, { v: T =>
        deserializationError(s"No Value type for this type of ${typeOf[T].getClass.getName}: " + v)
      })

      val valueFormat =
        jsonFormat.applyOrElse(valueType, { f: String =>
          deserializationError(s"No Json format for this type of $valueType")
        })

      valueFormat.asInstanceOf[JsonFormat[T]].write(value) match {
        case JsObject(fields) => JsObject(fields ++ Map(typeField -> JsString(valueType)))
        case _                => serializationError(s"${typeOf[T].getClass.getName} serialized not to a JSON object")
      }
    }

    def read(json: JsValue): T = json match {
      case JsObject(fields) =>
        val valueJson = JsObject(fields.filterNot(_._1 == typeField))
        fields(typeField) match {
          case JsString(valueType) =>
            val valueFormat = jsonFormat.applyOrElse(valueType, { t: String =>
              deserializationError(s"Unknown ${typeOf[T].getClass.getName} type ${fields(typeField)}")
            })
            valueFormat.read(valueJson)
          case _ =>
            deserializationError(s"Unknown ${typeOf[T].getClass.getName} type ${fields(typeField)}")
        }
      case _ =>
        deserializationError(s"Expected Json Object as ${typeOf[T].getClass.getName}, but got " + json)
    }
  }

  object GadtJsonFormat {

    def create[T: TypeTag](typeField: String)(typeValue: PartialFunction[T, String])(
            jsonFormat: PartialFunction[String, JsonFormat[_ <: T]]) = {

      new GadtJsonFormat[T](typeField, typeValue, jsonFormat)
    }
  }
}
