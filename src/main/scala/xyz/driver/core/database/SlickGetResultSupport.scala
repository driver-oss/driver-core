package xyz.driver.core.database

import java.util.Calendar

import slick.jdbc.GetResult
import xyz.driver.core.date.{Date, Month}
import xyz.driver.core.{Id, Name}
import xyz.driver.core.time.Time

trait SlickGetResultSupport {
  implicit def idGetResult[U]: GetResult[Id[U]] =
    GetResult(r => Id[U](r.nextString()))
  implicit def idOptionGetResult[U]: GetResult[Option[Id[U]]] =
    GetResult(_.nextStringOption().map(Id.apply[U]))

  implicit def nameGetResult[U]: GetResult[Name[U]] =
    GetResult(r => Name[U](r.nextString()))
  implicit def nameOptionGetResult[U]: GetResult[Option[Name[U]]] =
    GetResult(_.nextStringOption().map(Name.apply[U]))

  implicit val timeGetResult: GetResult[Time] =
    GetResult(r => Time(r.nextTimestamp.getTime))
  implicit val timeOptionGetResult: GetResult[Option[Time]] =
    GetResult(_.nextTimestampOption().map(t => Time(t.getTime)))

  private def javaDateToDate(jdate: java.util.Date): Date = {
    val cal = Calendar.getInstance
    cal.setTime(jdate)
    Date(cal.get(Calendar.YEAR), Month(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
  }

  implicit val dateGetResult: GetResult[Date] =
    GetResult(r => javaDateToDate(r.nextDate()))
  implicit val dateOptionGetResult: GetResult[Option[Date]] =
    GetResult(_.nextDateOption().map(javaDateToDate))
}

object SlickGetResultSupport extends SlickGetResultSupport
