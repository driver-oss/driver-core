package xyz.driver.core

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import xyz.driver.core.date.Date
import xyz.driver.core.time.Time

import scala.concurrent.Future
import com.typesafe.config.Config

package database {

  import java.sql.SQLDataException
  import java.time.{Instant, LocalDate}

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
    implicit def `java.time.LocalDate.columnType`: BaseColumnType[LocalDate]
  }

  object DateColumnTypes {
    trait SqlDate extends DateColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.time.Date.columnType`: BaseColumnType[Date] =
        MappedColumnType.base[Date, java.sql.Date](dateToSqlDate, sqlDateToDate)

      override implicit def `java.time.LocalDate.columnType`: BaseColumnType[LocalDate] =
        MappedColumnType.base[LocalDate, java.sql.Date](java.sql.Date.valueOf, _.toLocalDate)
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

  trait GenericIdColumnTypes[IdType] extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.GenericId.columnType`[T]: BaseColumnType[GenericId[T, IdType]]
  }

  object GenericIdColumnTypes {
    trait UUID extends GenericIdColumnTypes[java.util.UUID] {
      import profile.api._

      override implicit def `xyz.driver.core.GenericId.columnType`[T]: BaseColumnType[GenericId[T, java.util.UUID]] =
        MappedColumnType
          .base[GenericId[T, java.util.UUID], java.util.UUID](id => id.value, uuid => UuidId[T](uuid))
    }
    trait SerialId extends GenericIdColumnTypes[Long] {
      import profile.api._

      override implicit def `xyz.driver.core.GenericId.columnType`[T]: BaseColumnType[GenericId[T, Long]] =
        MappedColumnType.base[GenericId[T, Long], Long](_.value, serialId => NumericId[T](serialId))
    }
    trait NaturalId extends GenericIdColumnTypes[String] {
      import profile.api._

      override implicit def `xyz.driver.core.GenericId.columnType`[T]: BaseColumnType[GenericId[T, String]] =
        MappedColumnType.base[GenericId[T, String], String](_.value, Id[T])
    }
  }

  trait TimestampColumnTypes extends ColumnTypes {
    import profile.api._
    implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time]
    implicit def `java.time.Instant.columnType`: BaseColumnType[Instant]
  }

  object TimestampColumnTypes {
    trait SqlTimestamp extends TimestampColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time] =
        MappedColumnType.base[Time, java.sql.Timestamp](
          time => new java.sql.Timestamp(time.millis),
          timestamp => Time(timestamp.getTime))

      override implicit def `java.time.Instant.columnType`: BaseColumnType[Instant] =
        MappedColumnType.base[Instant, java.sql.Timestamp](java.sql.Timestamp.from, _.toInstant)
    }

    trait PrimitiveTimestamp extends TimestampColumnTypes {
      import profile.api._

      override implicit def `xyz.driver.core.time.Time.columnType`: BaseColumnType[Time] =
        MappedColumnType.base[Time, Long](_.millis, Time.apply)

      override implicit def `java.time.Instant.columnType`: BaseColumnType[Instant] =
        MappedColumnType.base[Instant, Long](_.toEpochMilli, Instant.ofEpochMilli)
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

  trait GenericKeyMappers extends ColumnTypes {
    import profile.api._

    def uuidKeyMapper[T] =
      MappedColumnType
        .base[UuidId[T], java.util.UUID](id => id.value, uuid => UuidId[T](uuid))
    def serialKeyMapper[T]  = MappedColumnType.base[NumericId[T], Long](_.value, serialId => NumericId[T](serialId))
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
