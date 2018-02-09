package xyz.driver.core.database

import xyz.driver.core.rest.errors.DatabaseException

import scala.reflect.ClassTag

trait Converters {
  def fromStringOrThrow[ADT](entityStr: String, mapper: (String => Option[ADT]), entityName: String): ADT =
    mapper(entityStr).getOrElse(throw DatabaseException(s"Invalid $entityName in database: $entityStr"))

  def expectValid[ADT](mapper: String => Option[ADT], query: String)(implicit ct: ClassTag[ADT]): ADT =
    fromStringOrThrow[ADT](query, mapper, ct.toString())
}
