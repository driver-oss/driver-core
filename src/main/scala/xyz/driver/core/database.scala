package xyz.driver.core

import slick.backend.DatabaseConfig
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import xyz.driver.core.time.Time

import scala.concurrent.{ExecutionContext, Future}
import scalaz.Monad

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
    val profile: JdbcProfile

    import profile.api._

    implicit def `xyz.driver.core.Id.columnType`[T] =
      MappedColumnType.base[Id[T], String](_.value, Id[T](_))

    implicit def `xyz.driver.core.Name.columnType`[T] =
      MappedColumnType.base[Name[T], String](_.value, Name[T](_))

    implicit def `xyz.driver.core.time.Time.columnType` =
      MappedColumnType.base[Time, Long](_.millis, Time(_))
  }

  trait DatabaseObject extends ColumnTypes {

    def createTables(): Future[Unit]
    def disconnect(): Unit
  }

  abstract class DatabaseObjectAdapter extends DatabaseObject {
    def createTables(): Future[Unit] = Future.successful(())
    def disconnect(): Unit           = {}
  }

  trait Dal {

    type T[_]
    implicit val monadT: Monad[T]

    def execute[D](operations: T[D]): Future[D]
    def executeTransaction[D](operations: T[D]): Future[D]
    def noAction[V](v: V): T[V]
    def customAction[R](action: => Future[R]): T[R]
  }

  class FutureDal(executionContext: ExecutionContext) extends Dal {

    implicit val exec = executionContext

    override type T[_] = Future[_]
    implicit val monadT: Monad[T] = new Monad[T] {
      override def point[A](a: => A): T[A]                  = Future(a)
      override def bind[A, B](fa: T[A])(f: A => T[B]): T[B] = fa.flatMap(a => f(a.asInstanceOf[A]))
    }

    def execute[D](operations: T[D]): Future[D]            = operations.asInstanceOf[Future[D]]
    def executeTransaction[D](operations: T[D]): Future[D] = operations.asInstanceOf[Future[D]]
    def noAction[V](v: V): T[V]                            = Future.successful(v)
    def customAction[R](action: => Future[R]): T[R]        = action
  }

  class SlickDal(database: Database, executionContext: ExecutionContext) extends Dal {

    import database.profile.api._

    implicit val exec = executionContext

    override type T[_] = slick.dbio.DBIO[_]
    val monadT: Monad[T] = new Monad[T] {
      override def point[A](a: => A): T[A]                  = DBIO.successful(a)
      override def bind[A, B](fa: T[A])(f: A => T[B]): T[B] = fa.flatMap(a => f(a.asInstanceOf[A]))
    }

    def execute[D](operations: T[D]): Future[D] = {
      database.database.run(operations.asInstanceOf[slick.dbio.DBIO[D]])
    }

    def executeTransaction[D](readOperations: T[D]): Future[D] = {
      database.database.run(readOperations.asInstanceOf[slick.dbio.DBIO[D]].transactionally)
    }

    def noAction[V](v: V): slick.dbio.DBIO[V]       = DBIO.successful(v)
    def customAction[R](action: => Future[R]): T[R] = DBIO.from(action)
  }
}
