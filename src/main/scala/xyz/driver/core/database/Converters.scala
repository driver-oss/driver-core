package xyz.driver.core.database

import xyz.driver.core.rest.errors.DatabaseException

import scala.reflect.ClassTag

trait Converters {
  def fromStringOrThrow[T](entityStr: String, mapper: (String => Option[T]), entityName: String): T =
    mapper(entityStr).getOrElse(throw DatabaseException(s"Invalid $entityName in database: $entityStr"))

  def expectValid[T](mapper: String => Option[T], query: String)(implicit ct: ClassTag[T]): T = {
    fromStringOrThrow[T](query, mapper, ct.toString())
  }
}
