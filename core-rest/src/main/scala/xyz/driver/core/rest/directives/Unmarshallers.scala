package xyz.driver.core
package rest
package directives

import java.util.UUID

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import spray.json.{JsString, JsValue, JsonParser, JsonReader, JsonWriter}

/** Akka-HTTP unmarshallers for custom core types. */
trait Unmarshallers {

  implicit def idUnmarshaller[A]: Unmarshaller[String, Id[A]] =
    Unmarshaller.strict[String, Id[A]] { str =>
      Id[A](UUID.fromString(str).toString)
    }

  implicit def uuidIdUnmarshaller[A]: Unmarshaller[String, UuidId[A]] =
    Unmarshaller.strict[String, UuidId[A]] { str =>
      UuidId[A](UUID.fromString(str))
    }

  implicit def numericIdUnmarshaller[A]: Unmarshaller[Long, NumericId[A]] =
    Unmarshaller.strict[Long, NumericId[A]] { x =>
      NumericId[A](x)
    }

  implicit def paramUnmarshaller[T](implicit reader: JsonReader[T]): Unmarshaller[String, T] =
    Unmarshaller.firstOf(
      Unmarshaller.strict((JsString(_: String)) andThen reader.read),
      stringToValueUnmarshaller[T]
    )

  implicit def revisionFromStringUnmarshaller[T]: Unmarshaller[String, Revision[T]] =
    Unmarshaller.strict[String, Revision[T]](Revision[T])

  val jsValueToStringMarshaller: Marshaller[JsValue, String] =
    Marshaller.strict[JsValue, String](value => Marshalling.Opaque[String](() => value.compactPrint))

  def valueToStringMarshaller[T](implicit jsonFormat: JsonWriter[T]): Marshaller[T, String] =
    jsValueToStringMarshaller.compose[T](jsonFormat.write)

  val stringToJsValueUnmarshaller: Unmarshaller[String, JsValue] =
    Unmarshaller.strict[String, JsValue](value => JsonParser(value))

  def stringToValueUnmarshaller[T](implicit jsonFormat: JsonReader[T]): Unmarshaller[String, T] =
    stringToJsValueUnmarshaller.map[T](jsonFormat.read)

}
