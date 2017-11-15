package xyz.driver.core.database

import slick.jdbc.GetResult
import xyz.driver.core.date.Date
import xyz.driver.core.time.Time
import xyz.driver.core.{Id, Name}

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

  implicit val dateGetResult: GetResult[Date] =
    GetResult(r => Date.fromJavaDate(r.nextDate()))
  implicit val dateOptionGetResult: GetResult[Option[Date]] =
    GetResult(_.nextDateOption().map(Date.fromJavaDate))
}

object SlickGetResultSupport extends SlickGetResultSupport
