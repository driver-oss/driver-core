package xyz.driver.core

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util

import com.fasterxml.jackson.databind.{BeanDescription, ObjectMapper}
import com.fasterxml.jackson.databind.`type`.ReferenceType
import io.swagger.converter._
import io.swagger.jackson.AbstractModelConverter
import io.swagger.models.{Model, ModelImpl}
import io.swagger.models.properties._
import io.swagger.util.{Json, PrimitiveType}
import spray.json._

object swagger {

  object CustomSwaggerJsonConverter {

    def stringProperty(pattern: Option[String] = None, example: Option[String] = None): Property = {
      make(new StringProperty()) { sp =>
        sp.required(true)
        example.foreach(sp.example)
        pattern.foreach(sp.pattern)
      }
    }

    def enumProperty[V](values: V*): Property = {
      make(new StringProperty()) { sp =>
        for (v <- values) sp._enum(v.toString)
        sp.setRequired(true)
      }
    }

    def numericProperty(example: Option[AnyRef] = None): Property = {
      make(PrimitiveType.DECIMAL.createProperty()) { dp =>
        dp.setRequired(true)
        example.foreach(dp.setExample)
      }
    }

    def booleanProperty(): Property = {
      make(new BooleanProperty()) { bp =>
        bp.setRequired(true)
      }
    }
  }

  class CustomSwaggerJsonConverter(mapper: ObjectMapper,
                                   customProperties: Map[Class[_], Property],
                                   customObjects: Map[Class[_], JsValue])
      extends AbstractModelConverter(mapper) {
    import CustomSwaggerJsonConverter._

    override def resolveProperty(`type`: Type,
                                 context: ModelConverterContext,
                                 annotations: Array[Annotation],
                                 chain: util.Iterator[ModelConverter]): Property = {
      val javaType = Json.mapper().constructType(`type`)

      Option(javaType.getRawClass) flatMap { cls =>
        customProperties.get(cls)
      } orElse {
        `type` match {
          case rt: ReferenceType if isOption(javaType.getRawClass) && chain.hasNext =>
            val nextType = rt.getContentType
            val nextResolved = Option(resolveProperty(nextType, context, annotations, chain)).getOrElse(
              chain.next().resolveProperty(nextType, context, annotations, chain))
            nextResolved.setRequired(false)
            Option(nextResolved)
          case t if chain.hasNext =>
            val nextResolved = chain.next().resolveProperty(t, context, annotations, chain)
            nextResolved.setRequired(true)
            Option(nextResolved)
          case _ =>
            Option.empty[Property]
        }
      } orNull
    }

    override def resolve(`type`: Type, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Model = {

      val javaType = Json.mapper().constructType(`type`)

      (getEnumerationInstance(javaType.getRawClass) match {
        case Some(enumInstance) => Option.empty[Model] // ignore scala enums
        case None =>
          val customObjectModel = customObjects.get(javaType.getRawClass).map { objectExampleJson =>
            val properties = objectExampleJson.asJsObject.fields.mapValues(parseJsonValueToSwaggerProperty).flatMap {
              case (key, value) => value.map(v => key -> v)
            }

            val beanDesc = _mapper.getSerializationConfig.introspect[BeanDescription](javaType)
            val name     = _typeName(javaType, beanDesc)

            make(new ModelImpl()) { model =>
              model.name(name)
              properties.foreach { case (field, property) => model.addProperty(field, property) }
            }
          }

          customObjectModel.orElse {
            if (chain.hasNext) {
              val next = chain.next()
              Option(next.resolve(`type`, context, chain))
            } else {
              Option.empty[Model]
            }
          }
      }).orNull
    }

    private def parseJsonValueToSwaggerProperty(jsValue: JsValue): Option[Property] = {
      import scala.collection.JavaConverters._

      jsValue match {
        case JsArray(elements) =>
          elements.headOption.flatMap(parseJsonValueToSwaggerProperty).map { itemProperty =>
            new ArrayProperty(itemProperty)
          }
        case JsObject(subFields) =>
          val subProperties = subFields.mapValues(parseJsonValueToSwaggerProperty).flatMap {
            case (key, value) => value.map(v => key -> v)
          }
          Option(new ObjectProperty(subProperties.asJava))
        case JsBoolean(value) => Option(booleanProperty())
        case JsNumber(value)  => Option(numericProperty(example = Option(value)))
        case JsString(value)  => Option(stringProperty(example = Option(value)))
        case _                => Option.empty[Property]
      }
    }

    private def getEnumerationInstance(cls: Class[_]): Option[Enumeration] = {
      if (cls.getFields.map(_.getName).contains("MODULE$")) {
        val javaUniverse = scala.reflect.runtime.universe
        val m            = javaUniverse.runtimeMirror(Thread.currentThread().getContextClassLoader)
        val moduleMirror = m.reflectModule(m.staticModule(cls.getName))
        moduleMirror.instance match {
          case enumInstance: Enumeration => Some(enumInstance)
          case _                         => None
        }
      } else {
        None
      }
    }

    private def isOption(cls: Class[_]): Boolean = cls.equals(classOf[scala.Option[_]])
  }
}
