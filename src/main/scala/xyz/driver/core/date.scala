package xyz.driver.core

import java.util.Calendar

import scala.util.Try

import scalaz.std.anyVal._
import scalaz.syntax.equal._

/**
  * Driver Date type and related validators/extractors.
  * Day, Month, and Year extractors are from ISO 8601 strings => driver...Date integers.
  * TODO: Decouple extractors from ISO 8601, as we might want to parse other formats.
  */
object date {

  type Day = Int @@ Day.type

  object Day {
    def apply(value: Int): Day = {
      require(1 to 31 contains value, "Day must be in range 1 <= value <= 31")
      value.asInstanceOf[Day]
    }

    def unapply(dayString: String): Option[Int] = {
      require(dayString.length === 2, s"ISO 8601 day string, DD, must have length 2: $dayString")
      Try(dayString.toInt).toOption.map(apply)
    }
  }

  type Month = Int @@ Month.type

  object Month {
    def apply(value: Int): Month = {
      require(0 to 11 contains value, "Month is zero-indexed: 0 <= value <= 11")
      value.asInstanceOf[Month]
    }
    val JANUARY   = Month(Calendar.JANUARY)
    val FEBRUARY  = Month(Calendar.FEBRUARY)
    val MARCH     = Month(Calendar.MARCH)
    val APRIL     = Month(Calendar.APRIL)
    val MAY       = Month(Calendar.MAY)
    val JUNE      = Month(Calendar.JUNE)
    val JULY      = Month(Calendar.JULY)
    val AUGUST    = Month(Calendar.AUGUST)
    val SEPTEMBER = Month(Calendar.SEPTEMBER)
    val OCTOBER   = Month(Calendar.OCTOBER)
    val NOVEMBER  = Month(Calendar.NOVEMBER)
    val DECEMBER  = Month(Calendar.DECEMBER)

    def unapply(monthString: String): Option[Month] = {
      require(monthString.length === 2, s"ISO 8601 month string, MM, must have length 2: $monthString")
      Try(monthString.toInt).toOption.map(isoM => apply(isoM - 1))
    }
  }

  type Year = Int @@ Year.type

  object Year {
    def apply(value: Int): Year = value.asInstanceOf[Year]

    def unapply(yearString: String): Option[Int] = {
      require(yearString.length === 4, s"ISO 8601 year string, YYYY, must have length 4: $yearString")
      Try(yearString.toInt).toOption.map(apply)
    }
  }

  final case class Date(year: Int, month: Month, day: Int) {
    override def toString = f"$year%04d-${month + 1}%02d-$day%02d"
  }

  object Date {
    implicit def dateOrdering: Ordering[Date] = Ordering.fromLessThan { (date1, date2) =>
      if (date1.year != date2.year) {
        date1.year < date2.year
      } else if (date1.month != date2.month) {
        date1.month < date2.month
      } else {
        date1.day < date2.day
      }
    }

    def fromString(dateString: String): Option[Date] = {
      dateString.split('-') match {
        case Array(Year(year), Month(month), Day(day)) => Some(Date(year, month, day))
        case _                                         => None
      }
    }

    def fromJavaDate(date: java.util.Date): Date = {
      val cal = Calendar.getInstance
      cal.setTime(date)
      Date(cal.get(Calendar.YEAR), Month(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
    }
  }
}
