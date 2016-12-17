package xyz.driver.core

import scala.concurrent.{ExecutionContext, Future}

import scalaz.{Monad, ListT, OptionT}
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import xyz.driver.core.time.Time

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
      MappedColumnType.base[Time, Long](_.millis, Time(_))
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

  trait Dal {

    protected type T[D]
    protected implicit val monadT: Monad[T]

    protected def execute[D](operations: T[D]): Future[D]
    protected def execute[D](readOperations: OptionT[T, D]): OptionT[Future, D]
    protected def execute[D](readOperations: ListT[T, D]): ListT[Future, D]

    protected def noAction[V](v: V): T[V]
    protected def customAction[R](action: => Future[R]): T[R]
  }

  class FutureDal(executionContext: ExecutionContext) extends Dal {
    import scalaz.std.scalaFuture._

    implicit val exec = executionContext

    override type T[D] = Future[D]
    implicit val monadT = implicitly[Monad[Future]]

    def execute[D](operations: T[D]): Future[D]                   = operations
    def execute[D](operations: OptionT[T, D]): OptionT[Future, D] = OptionT(operations.run)
    def execute[D](operations: ListT[T, D]): ListT[Future, D]     = ListT(operations.run)
    def noAction[V](v: V): T[V]                                   = Future.successful(v)
    def customAction[R](action: => Future[R]): T[R]               = action
  }

  class SlickDal(database: Database, executionContext: ExecutionContext) extends Dal {

    import database.profile.api._
    implicit val exec = executionContext
    override type T[D] = slick.dbio.DBIO[D]

    implicit protected class QueryOps[+E, U](query: Query[E, U, Seq]) {
      def resultT: ListT[T, U] = ListT[T, U](query.result.map(_.toList))
    }

    override implicit val monadT: Monad[T] = new Monad[T] {
      override def point[A](a: => A): T[A]                  = DBIO.successful(a)
      override def bind[A, B](fa: T[A])(f: A => T[B]): T[B] = fa.flatMap(f)
    }

    override def execute[D](readOperations: T[D]): Future[D] = {
      database.database.run(readOperations.transactionally)
    }

    override def execute[D](readOperations: OptionT[T, D]): OptionT[Future, D] = {
      OptionT(database.database.run(readOperations.run.transactionally))
    }

    override def execute[D](readOperations: ListT[T, D]): ListT[Future, D] = {
      ListT(database.database.run(readOperations.run.transactionally))
    }

    override def noAction[V](v: V): T[V]                     = DBIO.successful(v)
    override def customAction[R](action: => Future[R]): T[R] = DBIO.from(action)
  }
}
