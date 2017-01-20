package xyz.driver.core

import scala.concurrent.Future

import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import xyz.driver.core.time.Time
import xyz.driver.core.date.Date

package database {

  trait Database {
    val profile: JdbcProfile
    val database: JdbcProfile#Backend#Database
  }

  object Database {

    def fromConfig(databaseName: String): Database = {
      val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig(databaseName)

      new Database {
        val profile: JdbcProfile                   = dbConfig.driver
        val database: JdbcProfile#Backend#Database = dbConfig.db
      }
    }
  }

  trait ColumnTypes {
    val profile: JdbcProfile
    import profile.api._

    implicit def `xyz.driver.core.Id.columnType`[T]: BaseColumnType[Id[T]]

    implicit def `xyz.driver.core.Name.columnType`[T]: BaseColumnType[Name[T]] =
      MappedColumnType.base[Name[T], String](_.value, Name[T](_))

    implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time] =
      MappedColumnType.base[Time, java.sql.Timestamp](time => new java.sql.Timestamp(time.millis),
                                                      timestamp => Time(timestamp.getTime))

    implicit def `xyz.driver.core.time.Date.columnType`: BaseColumnType[Date] =
      MappedColumnType.base[Date, java.sql.Date](dateToSqlDate(_), sqlDateToDate(_))
  }

  object ColumnTypes {
    trait UUID extends ColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType
          .base[Id[T], java.util.UUID](id => java.util.UUID.fromString(id.value), uuid => Id[T](uuid.toString))
    }
    trait SerialId extends ColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType.base[Id[T], Long](_.value.toLong, serialId => Id[T](serialId.toString))
    }
    trait NaturalId extends ColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType.base[Id[T], String](_.value, Id[T](_))
    }
  }

  trait DatabaseObject extends ColumnTypes {
    def createTables(): Future[Unit]
    def disconnect(): Unit
  }

  abstract class DatabaseObjectAdapter extends DatabaseObject {
    def createTables(): Future[Unit] = Future.successful(())
    def disconnect(): Unit           = {}
  }
}
