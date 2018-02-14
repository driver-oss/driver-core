package xyz.driver.core

import magnolia._
import spray.json._

import scala.language.experimental.macros

sealed trait JsonSealedTrait
final class gadt(val typeFieldName: String = "type") extends scala.annotation.StaticAnnotation with JsonSealedTrait
final class enum extends scala.annotation.StaticAnnotation with JsonSealedTrait

trait JsonFormatDerivation extends DefaultJsonProtocol {
  type Typeclass[T] = JsonFormat[T]

  def combine[T](ctx: CaseClass[JsonFormat, T]): JsonFormat[T] = new JsonFormat[T] {
    override def write(value: T): JsValue = if (ctx.isObject) {
      JsString(ctx.typeName.short)
    } else {
      val fields: Seq[(String, JsValue)] = ctx.parameters.map { param =>
        param.label -> param.typeclass.write(param.dereference(value))
      }
      JsObject(fields: _*)
    }

    override def read(value: JsValue): T = value match {
      case obj: JsObject =>
        ctx.construct { param =>
          param.typeclass.read(obj.fields(param.label))
        }
      case str: JsString if ctx.isObject && str.value == ctx.typeName.short =>
        ctx.rawConstruct(Seq.empty)

      case js =>
        deserializationError(s"Cannot read JSON '$js' as a ${ctx.typeName.full}")
    }
  }

  def dispatch[T](ctx: SealedTrait[JsonFormat, T]): JsonFormat[T] = new JsonFormat[T] {
    override def write(value: T): JsValue = ctx.annotations.find(_.isInstanceOf[JsonSealedTrait]).getOrElse(new gadt("type")) match {
      case _: enum =>
        ctx.dispatch(value) { sub =>
          JsString(sub.typeName.short)
        }

      case g: gadt =>
        ctx.dispatch(value) { sub =>
          val obj = sub.typeclass.write(sub.cast(value)).asJsObject
          JsObject((obj.fields ++ Map(g.typeFieldName -> JsString(sub.typeName.short))).toSeq: _*)
        }
    }

    override def read(value: JsValue): T = ctx.annotations.find(_.isInstanceOf[JsonSealedTrait]).getOrElse(new gadt("type")) match {
      case _: enum =>
        value match {
          case str: JsString =>
            val x = ctx.subtypes.find(_.typeName.short == str.value).getOrElse(deserializationError(
              s"Cannot deserialize JSON to ${ctx.typeName.full} because type '${str}' has an unsupported value."))
            x.typeclass.read(str)
          case js =>
            deserializationError(s"Cannot read JSON '$js' as a ${ctx.typeName.full}")
        }

      case g: gadt =>
        value match {
          case obj: JsObject if obj.fields.contains(g.typeFieldName) =>
            val fieldName = obj.fields(g.typeFieldName).convertTo[String]

            ctx.subtypes.find(_.typeName.short == fieldName) match {
              case Some(tpe) => tpe.typeclass.read(obj)
              case None =>
                deserializationError(
                  s"Cannot deserialize JSON to ${ctx.typeName.full} because type field '${fieldName}' has an unsupported value.")
            }

          case js =>
            deserializationError(s"Cannot read JSON '$js' as a ${ctx.typeName}")
        }
    }
  }

  implicit def gen[T]: JsonFormat[T] = macro Magnolia.gen[T]

}
object JsonFormatDerivation extends JsonFormatDerivation

trait DerivedFormats extends JsonFormatDerivation
