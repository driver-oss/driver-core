package xyz.driver.core

import akka.http.scaladsl.unmarshalling.Unmarshaller
import enumeratum._

@deprecated(
  "Using static JSON formats from singleton objects can require to many wildcard imports. It is " +
    "recommended to stack format traits into a single protocol.",
  "driver-core 1.11.5"
)
object json extends CoreJsonFormats with rest.Unmarshallers with rest.PathMatchers { self =>

  object enumeratum {
    def enumUnmarshaller[T <: EnumEntry](enum: Enum[T]): Unmarshaller[String, T] =
      rest.Directives.enumUnmarshaller(enum)
    type HasJsonFormat[T <: EnumEntry]  = self.HasJsonFormat[T]
    type EnumJsonFormat[T <: EnumEntry] = self.EnumJsonFormat[T]
  }

}
