package xyz.driver.core.database

import slick.jdbc.GetResult
import xyz.driver.core.date.Date
import xyz.driver.core.time.Time
import xyz.driver.core.{Id, Name}

trait SlickGetResultSupport {
  implicit def GetId[U]: GetResult[Id[U]] =
    GetResult(r => Id[U](r.nextString()))
  implicit def GetIdOption[U]: GetResult[Option[Id[U]]] =
    GetResult(_.nextStringOption().map(Id.apply[U]))

  implicit def GetName[U]: GetResult[Name[U]] =
    GetResult(r => Name[U](r.nextString()))
  implicit def GetNameOption[U]: GetResult[Option[Name[U]]] =
    GetResult(_.nextStringOption().map(Name.apply[U]))

  implicit val GetTime: GetResult[Time] =
    GetResult(r => Time(r.nextTimestamp.getTime))
  implicit val GetTimeOption: GetResult[Option[Time]] =
    GetResult(_.nextTimestampOption().map(t => Time(t.getTime)))

  implicit val GetDate: GetResult[Date] =
    GetResult(r => sqlDateToDate(r.nextDate()))
  implicit val GetDateOption: GetResult[Option[Date]] =
    GetResult(_.nextDateOption().map(sqlDateToDate))
}

object SlickGetResultSupport extends SlickGetResultSupport
