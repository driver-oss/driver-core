package xyz.driver.core

import java.util.Calendar

import scalaz.{@@, Tag}

object date {

  type Month = Int @@ Month.type
  private[date] def tagMonth(value: Int): Month = Tag.of[Month.type](value)

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
    def iso8601: String = f"$year%04d-${Tag.unwrap(month) + 1}%02d-$day%02d"
  }

  object Date {
    def fromJavaDate(date: java.util.Date) = {
      val cal = Calendar.getInstance()
      cal.setTime(date)
      Date(cal.get(Calendar.YEAR), tagMonth(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
    }
  }
}
