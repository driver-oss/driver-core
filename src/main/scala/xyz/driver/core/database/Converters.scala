package xyz.driver.core.database

import xyz.driver.core.rest.errors.DatabaseException

import scala.reflect.ClassTag

/**
  * Helper methods for converting between table rows and Scala objects
  */
trait Converters {
  def fromStringOrThrow[ADT](entityStr: String, mapper: (String => Option[ADT]), entityName: String): ADT =
    mapper(entityStr).getOrElse(throw DatabaseException(s"Invalid $entityName in database: $entityStr"))

  def fromOrThrow[S, ADT](query: S, mapper: (S => Option[ADT]), entityName: String): ADT = {
    mapper(query).getOrElse(throw DatabaseException(s"Invalid $entityName in database: ${query.toString}"))
  }

  def expectValid[ADT](mapper: String => Option[ADT], query: String)(implicit ct: ClassTag[ADT]): ADT =
    fromStringOrThrow[ADT](query, mapper, ct.toString())

  def expectValid[S, ADT](mapper: S => Option[ADT], query: S)(implicit ct: ClassTag[ADT]): ADT = {
    fromOrThrow[S, ADT](query, mapper, ct.toString)
  }

  def expectExistsAndValid[ADT](mapper: String => Option[ADT], query: Option[String], contextMsg: String = "")(
      implicit ct: ClassTag[ADT]): ADT = {
    expectValid[ADT](mapper, query.getOrElse(throw DatabaseException(contextMsg)))
  }

  def expectValidOrEmpty[ADT](mapper: String => Option[ADT], query: Option[String], contextMsg: String = "")(
      implicit ct: ClassTag[ADT]): Option[ADT] = {
    query.map(expectValid[ADT](mapper, _))
  }
}
