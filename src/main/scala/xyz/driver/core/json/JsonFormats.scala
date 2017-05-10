package xyz.driver.core
package json

import spray.json.{DeserializationException, JsNumber, _}

import scala.reflect.runtime.universe._

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
    case _ => deserializationError("Expected string as enumeration value, but got " + json.toString)
  }
}

class ValueClassFormat[T: TypeTag](writeValue: T => BigDecimal, create: BigDecimal => T) extends JsonFormat[T] {
  def write(valueClass: T) = JsNumber(writeValue(valueClass))
  def read(json: JsValue): T = json match {
    case JsNumber(value) => create(value)
    case _               => deserializationError(s"Expected number as ${typeOf[T].getClass.getName}, but got " + json.toString)
  }
}

class GadtJsonFormat[T: TypeTag](typeField: String,
  typeValue: PartialFunction[T, String],
  jsonFormat: PartialFunction[String, JsonFormat[_ <: T]])
    extends RootJsonFormat[T] {

  def write(value: T): JsValue = {

    val valueType = typeValue.applyOrElse(value, { v: T =>
      deserializationError(s"No Value type for this type of ${typeOf[T].getClass.getName}: " + v.toString)
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
      deserializationError(s"Expected Json Object as ${typeOf[T].getClass.getName}, but got " + json.toString)
  }
}

object GadtJsonFormat {

  def create[T: TypeTag](typeField: String)(typeValue: PartialFunction[T, String])(
    jsonFormat: PartialFunction[String, JsonFormat[_ <: T]]) = {

    new GadtJsonFormat[T](typeField, typeValue, jsonFormat)
  }
}
