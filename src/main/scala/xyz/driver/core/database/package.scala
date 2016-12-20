package xyz.driver.core

import java.sql.{Date => SqlDate}
import java.util.Calendar

import date.Date
import slick.dbio.{DBIOAction, NoStream}

package object database {

  type Schema = {
    def create: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
    def drop: DBIOAction[Unit, NoStream, slick.dbio.Effect.Schema]
  }

  private[database] def sqlDateToDate(sqlDate: SqlDate): Date = {
    // NOTE: SQL date does not have a time component, so this date
    // should only be interpreted in the running JVMs timezone.
    val cal = Calendar.getInstance()
    cal.setTime(sqlDate)
    Date(cal.get(Calendar.YEAR), date.tagMonth(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
  }

  private[database] def dateToSqlDate(date: Date): SqlDate = {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, date.year - 1900)
    cal.set(Calendar.MONTH, date.month)
    cal.set(Calendar.DAY_OF_MONTH, date.day)
    new SqlDate(cal.getTime.getTime)
  }
}
