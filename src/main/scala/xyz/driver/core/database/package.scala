package xyz.driver.core

import java.sql.{Date => SqlDate}
import java.util.Calendar

import date.{Date, javaDateToDate}
import slick.dbio.{DBIOAction, NoStream}

package object database {
  type Schema = {
    def create: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
    def drop: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
  }

  private[database] def sqlDateToDate(date: SqlDate): Date = javaDateToDate(date)

  private[database] def dateToSqlDate(date: Date): SqlDate = {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, date.year - 1900)
    cal.set(Calendar.MONTH, date.month)
    cal.set(Calendar.DAY_OF_MONTH, date.day)
    new SqlDate(cal.getTime.getTime)
  }
}
