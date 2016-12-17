package xyz.driver.core

import java.util.Calendar

object date {

  type Month = Int @@ Month.type
  private[date] def tagMonth(value: Int): Month = value.asInstanceOf[Month]

  object Month {
    val JANUARY   = tagMonth(Calendar.JANUARY)
    val FEBRUARY  = tagMonth(Calendar.FEBRUARY)
    val MARCH     = tagMonth(Calendar.MARCH)
    val APRIL     = tagMonth(Calendar.APRIL)
    val MAY       = tagMonth(Calendar.MAY)
    val JUNE      = tagMonth(Calendar.JUNE)
    val JULY      = tagMonth(Calendar.JULY)
    val AUGUST    = tagMonth(Calendar.AUGUST)
    val SEPTEMBER = tagMonth(Calendar.SEPTEMBER)
    val OCTOBER   = tagMonth(Calendar.OCTOBER)
    val DECEMBER  = tagMonth(Calendar.DECEMBER)
  }

  final case class Date(year: Int, month: Month, day: Int) {
    def iso8601: String = f"$year%04d-${month + 1}%02d-$day%02d"
    def toJavaSqlDate   = new java.sql.Date(toJavaDate.getTime)
    def toJavaDate: java.util.Date = {
      val cal = Calendar.getInstance()
      cal.set(Calendar.YEAR, year - 1900)
      cal.set(Calendar.MONTH, month)
      cal.set(Calendar.DAY_OF_MONTH, day)
      cal.getTime
    }
  }

  object Date {
    def parseIso(iso: String): Option[Date] = {
      util.Try(iso.split("-").map(_.toInt)).toOption collect {
        case Array(year, month, day) => Date(year, tagMonth(month), day)
      }
    }
    def fromJavaDate(date: java.util.Date) = {
      val cal = Calendar.getInstance()
      cal.setTime(date)
      Date(cal.get(Calendar.YEAR), tagMonth(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
    }
  }
}
