package xyz.driver.core

import java.util.Calendar

object date {

  type Month = Int @@ Month.type
  private[core] def tagMonth(value: Int): Month = value.asInstanceOf[Month]

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

  final case class Date(year: Int, month: Month, day: Int)

  private[core] def javaDateToDate(javaDate: java.util.Date): Date = {
    val cal = Calendar.getInstance()
    cal.setTime(javaDate)
    Date(
      cal.get(Calendar.YEAR),
      date.tagMonth(cal.get(Calendar.MONTH)),
      cal.get(Calendar.DAY_OF_MONTH))
  }


}
