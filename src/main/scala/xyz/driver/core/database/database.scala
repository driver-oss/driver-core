package xyz.driver.core

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import xyz.driver.core.date.Date
import xyz.driver.core.time.Time

import scala.concurrent.Future
import com.typesafe.config.Config

package database {

  import java.sql.SQLDataException

  import eu.timepit.refined.api.{Refined, Validate}
  import eu.timepit.refined.refineV

  trait Database {
    val profile: JdbcProfile
    val database: JdbcProfile#Backend#Database
  }

  object Database {
    def fromConfig(config: Config, databaseName: String): Database = {
      val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig(databaseName, config)

      new Database {
        val profile: JdbcProfile                   = dbConfig.profile
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

  trait RefinedColumnTypes[T, Predicate] extends ColumnTypes {
    import profile.api._
    implicit def `eu.timepit.refined.api.Refined`(
        implicit columnType: BaseColumnType[T],
        validate: Validate[T, Predicate]): BaseColumnType[T Refined Predicate]
  }

  object RefinedColumnTypes {
    trait RefinedValue[T, Predicate] extends RefinedColumnTypes[T, Predicate] {
      import profile.api._
      override implicit def `eu.timepit.refined.api.Refined`(
          implicit columnType: BaseColumnType[T],
          validate: Validate[T, Predicate]): BaseColumnType[T Refined Predicate] =
        MappedColumnType.base[T Refined Predicate, T](
          _.value, { dbValue =>
            refineV[Predicate](dbValue) match {
              case Left(refinementError) =>
                throw new SQLDataException(
                  s"Value in the database doesn't match the refinement constraints: $refinementError")
              case Right(refinedValue) =>
                refinedValue
            }
          }
        )
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
        MappedColumnType.base[Id[T], String](_.value, Id[T])
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
        MappedColumnType.base[Time, java.sql.Timestamp](
          time => new java.sql.Timestamp(time.millis),
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
    def naturalKeyMapper[T] = MappedColumnType.base[Id[T], String](_.value, Id[T])
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
