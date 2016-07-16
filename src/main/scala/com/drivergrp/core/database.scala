package com.drivergrp.core

import com.drivergrp.core.id.{Id, Name}
import scala.concurrent.Future
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile


object database {

  trait Database {
    val profile: JdbcProfile
    val database: JdbcProfile#Backend#Database
  }

  object Database {

    def fromConfig(databaseName: String): Database = {
      val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig(databaseName)

      new Database {
        val profile: JdbcProfile = dbConfig.driver
        val database: JdbcProfile#Backend#Database = dbConfig.db
      }
    }
  }


  trait IdColumnTypes {
    val database: Database

    import database.profile.api._

    implicit def idColumnType[T] =
      MappedColumnType.base[Id[T], Long]({ id => id: Long }, { id => Id[T](id) })

    implicit def nameColumnType[T] =
      MappedColumnType.base[Name[T], String]({ name => name: String }, { name => Name[T](name) })
  }


  trait DatabaseObject extends IdColumnTypes {

    def createTables(): Future[Unit]
    def disconnect(): Unit
  }

  abstract class DatabaseObjectAdapter extends DatabaseObject {
    def createTables(): Future[Unit] = Future.successful(())
    def disconnect(): Unit = {}
  }
}
