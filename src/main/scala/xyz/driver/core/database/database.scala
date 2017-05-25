package xyz.driver.core

import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import xyz.driver.core.date.Date
import xyz.driver.core.time.Time

import scala.concurrent.Future

package database {

  import com.typesafe.config.Config

  trait Database {
    val profile: JdbcProfile
    val database: JdbcProfile#Backend#Database
  }

  object Database {
    def fromConfig(config: Config, databaseName: String): Database = {
      val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig(databaseName, config)

      new Database {
        val profile: JdbcProfile                   = dbConfig.driver
        val database: JdbcProfile#Backend#Database = dbConfig.db
      }
    }

    def fromConfig(databaseName: String): Database = {
      fromConfig(com.typesafe.config.ConfigFactory.load(), databaseName)
    }
  }

  trait ColumnTypes {
    val profile: JdbcProfile
  }

  trait NameColumnTypes extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.Name.columnType`[T]: BaseColumnType[Name[T]]
  }

  object NameColumnTypes {
    trait StringName extends NameColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Name.columnType`[T]: BaseColumnType[Name[T]] =
        MappedColumnType.base[Name[T], String](_.value, Name[T])
    }
  }

  trait DateColumnTypes extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.time.Date.columnType`: BaseColumnType[Date]
  }

  object DateColumnTypes {
    trait SqlDate extends DateColumnTypes {
      import profile.api._
      override implicit def `xyz.driver.core.time.Date.columnType`: BaseColumnType[Date] =
        MappedColumnType.base[Date, java.sql.Date](dateToSqlDate, sqlDateToDate)
    }
  }

  trait IdColumnTypes extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.Id.columnType`[T]: BaseColumnType[Id[T]]
  }

  object IdColumnTypes {
    trait UUID extends IdColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType
          .base[Id[T], java.util.UUID](id => java.util.UUID.fromString(id.value), uuid => Id[T](uuid.toString))
    }
    trait SerialId extends IdColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType.base[Id[T], Long](_.value.toLong, serialId => Id[T](serialId.toString))
    }
    trait NaturalId extends IdColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.Id.columnType`[T] =
        MappedColumnType.base[Id[T], String](_.value, Id[T](_))
    }
  }

  trait TimestampColumnTypes extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time]
  }

  object TimestampColumnTypes {
    trait SqlTimestamp extends TimestampColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time] =
        MappedColumnType.base[Time, java.sql.Timestamp](time => new java.sql.Timestamp(time.millis),
                                                        timestamp => Time(timestamp.getTime))
    }

    trait PrimitiveTimestamp extends TimestampColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time] =
        MappedColumnType.base[Time, Long](_.millis, Time(_))
    }
  }

  trait KeyMappers extends ColumnTypes {
    import profile.api._

    def uuidKeyMapper[T] =
      MappedColumnType
        .base[Id[T], java.util.UUID](id => java.util.UUID.fromString(id.value), uuid => Id[T](uuid.toString))
    def serialKeyMapper[T]  = MappedColumnType.base[Id[T], Long](_.value.toLong, serialId => Id[T](serialId.toString))
    def naturalKeyMapper[T] = MappedColumnType.base[Id[T], String](_.value, Id[T](_))
  }

  trait GeneratedTables extends ColumnTypes {
    import slick.dbio._

    val profile: JdbcProfile
    def schema: profile.SchemaDescription

    def createNamespaceSchema: StreamingDBIO[Vector[Unit], Unit]
    def dropNamespaceSchema: StreamingDBIO[Vector[Unit], Unit]
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
