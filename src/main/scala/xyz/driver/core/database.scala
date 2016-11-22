package xyz.driver.core

import slick.backend.DatabaseConfig
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import xyz.driver.core.time.Time

import scala.concurrent.Future

object database {

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

  type Schema = {
    def create: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
    def drop: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
  }

  trait ColumnTypes {
    val database: Database

    import database.profile.api._

    implicit def `xyz.driver.core.Id.columnType`[T] =
      MappedColumnType.base[Id[T], Long](id => id: Long, Id[T](_))

    implicit def `xyz.driver.core.Name.columnType`[T] =
      MappedColumnType.base[Name[T], String](name => name: String, Name[T](_))

    implicit def `xyz.driver.core.time.Time.columnType` =
      MappedColumnType.base[Time, Long](time => time.millis, Time(_))
  }

  trait DatabaseObject extends ColumnTypes {

//    implicit val exec: ExecutionContext

    def createTables(): Future[Unit]
    def disconnect(): Unit

//    def ensureTableExist(schemas: Seq[Schema]): Future[Unit] =
//      for {
//        dropping <- Future.sequence(schemas.map { schema =>
//                     database.database.run(schema.drop).recover { case _: Throwable => () }
//                   })
//        creation <- Future.sequence(schemas.map { schema =>
//                     database.database.run(schema.create).recover { case _: Throwable => () }
//                   })
//      } yield ()
  }

  abstract class DatabaseObjectAdapter extends DatabaseObject {
    def createTables(): Future[Unit] = Future.successful(())
    def disconnect(): Unit           = {}
  }
}
