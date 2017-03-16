package xyz.driver.core

import java.util.Calendar

object date {

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
      util.Try(dateString.split("-").map(_.toInt)).toOption collect {
        case Array(year, month, day) if (1 to 12 contains month) && (1 to 31 contains day) =>
          Date(year, Month(month - 1), day)
      }
    }
  }
}
