package xyz.driver.test.hsql

import java.nio.file.{Files, Paths}

import slick.dbio.DBIO

trait HsqlTestDatabase {
  def insertTestData(database: xyz.driver.core.database.Database, filePath: String): DBIO[Int] = {
    import database.profile.api._

    val file    = Paths.get(filePath)
    val sqlLine = new String(Files.readAllBytes(file), "UTF-8")

    val createProcedure =
      sqlu"""CREATE PROCEDURE INSERT_TEST_DATA()
               MODIFIES SQL DATA
             BEGIN ATOMIC
             #$sqlLine
             END;
           """
    val callProcedure = sqlu"""{call INSERT_TEST_DATA()}"""
    val dropProcedure = sqlu"""drop PROCEDURE INSERT_TEST_DATA;"""

    createProcedure >> callProcedure >> dropProcedure
  }
}
