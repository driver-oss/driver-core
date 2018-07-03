package xyz.driver.core.rest

import java.util.UUID

import akka.http.scaladsl.marshalling.{Marshaller, Marshalling}
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.{PathMatchers => AkkaPathMatchers, Directives => AkkaDirectives, _}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import enumeratum._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import spray.json._
import xyz.driver.core.time.Time
import xyz.driver.core.{CoreJsonFormats, Id, Name, NonEmptyName}

trait Directives  extends AkkaDirectives with Unmarshallers with PathMatchers
object Directives extends Directives

trait PathMatchers {

  private def UuidInPath[T]: PathMatcher1[Id[T]] =
    AkkaPathMatchers.JavaUUID.map((id: UUID) => Id[T](id.toString.toLowerCase))

  def IdInPath[T]: PathMatcher1[Id[T]] = UuidInPath[T] | new PathMatcher1[Id[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Id[T](segment)))
      case _                           => Unmatched
    }
  }

  def NameInPath[T]: PathMatcher1[Name[T]] = new PathMatcher1[Name[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Name[T](segment)))
      case _                           => Unmatched
    }
  }

  def TimeInPath: PathMatcher1[Time] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(Time(string.toLong))
      catch { case _: IllegalArgumentException => None }
    }

  def NonEmptyNameInPath[T]: PathMatcher1[NonEmptyName[T]] = new PathMatcher1[NonEmptyName[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) =>
        refineV[NonEmpty](segment) match {
          case Left(_)               => Unmatched
          case Right(nonEmptyString) => Matched(tail, Tuple1(NonEmptyName[T](nonEmptyString)))
        }
      case _ => Unmatched
    }
  }

}

trait Unmarshallers {

  implicit def paramUnmarshaller[T](implicit reader: JsonReader[T]): Unmarshaller[String, T] =
    Unmarshaller.firstOf(
      Unmarshaller.strict((JsString(_: String)) andThen reader.read),
      stringToValueUnmarshaller[T]
    )

  def enumUnmarshaller[T <: EnumEntry](enum: Enum[T]): Unmarshaller[String, T] =
    Unmarshaller.strict { value =>
      enum.withNameOption(value).getOrElse(CoreJsonFormats.unrecognizedValue(value, enum.values))
    }

  val jsValueToStringMarshaller: Marshaller[JsValue, String] =
    Marshaller.strict[JsValue, String](value => Marshalling.Opaque[String](() => value.compactPrint))

  def valueToStringMarshaller[T](implicit jsonFormat: JsonWriter[T]): Marshaller[T, String] =
    jsValueToStringMarshaller.compose[T](jsonFormat.write)

  val stringToJsValueUnmarshaller: Unmarshaller[String, JsValue] =
    Unmarshaller.strict[String, JsValue](value => value.parseJson)

  def stringToValueUnmarshaller[T](implicit jsonFormat: JsonReader[T]): Unmarshaller[String, T] =
    stringToJsValueUnmarshaller.map[T](jsonFormat.read)

}
