package xyz.driver.core

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.collection.NonEmpty
import spray.json._
import xyz.driver.core
import xyz.driver.core.time.Time

import scala.reflect.runtime.universe._

@deprecated(
  "Json utilities has been split into different locations. Path matchers have been moved to " +
    "xyz.driver.core.rest.Directives and formats to xyz.driver.core.CoreJsonFormats. Please extend these traits or " +
    "import their companion objects instead.",
  "driver-core 1.9.0"
)
object json {

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def IdInPath[T]: PathMatcher1[Id[T]] = rest.Directives.IdInPath[T]

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def idFormat[T] = CoreJsonFormats.idFormat[T]

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def NameInPath[T]: PathMatcher1[Name[T]] = rest.Directives.NameInPath[T]

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def nameFormat[T] = CoreJsonFormats.nameFormat[T]

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def TimeInPath: PathMatcher1[Time] = rest.Directives.TimeInPath

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val timeFormat = CoreJsonFormats.timeFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val dateFormat = CoreJsonFormats.dateFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val monthFormat = CoreJsonFormats.monthFormat

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def RevisionInPath[T]: PathMatcher1[Revision[T]] = rest.Directives.RevisionInPath

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def revisionFromStringUnmarshaller[T]: Unmarshaller[String, Revision[T]] =
    CoreJsonFormats.revisionFromStringUnmarshaller[T]

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def revisionFormat[T] = CoreJsonFormats.revisionFormat[T]

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val base64Format = CoreJsonFormats.base64Format

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val emailFormat = CoreJsonFormats.emailFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val phoneNumberFormat = CoreJsonFormats.phoneNumberFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val authCredentialsFormat = CoreJsonFormats.authCredentialsFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  type inetAddressFormat = CoreJsonFormats.inetAddressFormat.type
  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit val inetAddressFormat = CoreJsonFormats.inetAddressFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  type EnumJsonFormat[T] = CoreJsonFormats.EnumJsonFormat[T]
  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def newEnumJsonFormat[T](mapping: (String, T)*) = new core.CoreJsonFormats.EnumJsonFormat[T](mapping: _*)

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  type ValueClassFormat[T] = CoreJsonFormats.ValueClassFormat[T]
  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def newValueClassFormat[T: TypeTag](writeValue: T => BigDecimal, create: BigDecimal => T) =
    new CoreJsonFormats.ValueClassFormat[T](writeValue, create)

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  type GadtJsonFormat[T] = CoreJsonFormats.GadtJsonFormat[T]
  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def newGadtJsonFormat[T: TypeTag](
      typeField: String,
      typeValue: PartialFunction[T, String],
      jsonFormat: PartialFunction[String, JsonFormat[_ <: T]]) =
    new CoreJsonFormats.GadtJsonFormat[T](typeField, typeValue, jsonFormat)
  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  val GadtJsonFormat = CoreJsonFormats.GadtJsonFormat

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def refinedJsonFormat[T, Predicate](
      implicit valueFormat: JsonFormat[T],
      validate: Validate[T, Predicate]): JsonFormat[Refined[T, Predicate]] =
    CoreJsonFormats.refinedJsonFormat[T, Predicate](valueFormat, validate)

  @deprecated("Moved to xyz.driver.core.rest.Directives", "driver-core 1.9.0")
  def NonEmptyNameInPath[T]: PathMatcher1[NonEmptyName[T]] = rest.Directives.NonEmptyNameInPath[T]

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  implicit def nonEmptyNameFormat[T](implicit nonEmptyStringFormat: JsonFormat[Refined[String, NonEmpty]]) =
    CoreJsonFormats.nonEmptyNameFormat[T](nonEmptyStringFormat)

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  val jsValueToStringMarshaller: Marshaller[JsValue, String] = CoreJsonFormats.jsValueToStringMarshaller

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  def valueToStringMarshaller[T](implicit jsonFormat: JsonWriter[T]): Marshaller[T, String] =
    CoreJsonFormats.valueToStringMarshaller[T](jsonFormat)

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  val stringToJsValueUnmarshaller: Unmarshaller[String, JsValue] =
    CoreJsonFormats.stringToJsValueUnmarshaller

  @deprecated(
    "Moved to format trait xyz.driver.core.CoreJsonFormats. Extend the trait or import its companion object instead.",
    "driver-core 1.9.0")
  def stringToValueUnmarshaller[T](implicit jsonFormat: JsonReader[T]): Unmarshaller[String, T] =
    CoreJsonFormats.stringToValueUnmarshaller[T](jsonFormat)

}
