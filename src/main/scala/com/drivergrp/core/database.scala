package com.drivergrp.core

import com.drivergrp.core.id.{Id, Name}

import scala.concurrent.Future


object database {

  import slick.backend.DatabaseConfig
  import slick.driver.JdbcProfile


  trait DatabaseModule {
    val profile: JdbcProfile
    val database: JdbcProfile#Backend#Database
  }

  trait ConfigDatabaseModule extends DatabaseModule {

    protected def databaseConfigKey: String

    private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig(databaseConfigKey)

    val profile: JdbcProfile = dbConfig.driver
    val database: JdbcProfile#Backend#Database = dbConfig.db
  }

  trait DatabaseObject {
    def createTables(): Future[Unit]
    def disconnect(): Unit
  }

  trait IdColumnTypes {
    this: DatabaseModule =>

    import profile.api._

    implicit def idColumnType[T] =
      MappedColumnType.base[Id[T], Long]({ id => id: Long }, { id => Id[T](id) })

    implicit def nameColumnType[T] =
      MappedColumnType.base[Name[T], String]({ name => name: String }, { name => Name[T](name) })
  }
}
