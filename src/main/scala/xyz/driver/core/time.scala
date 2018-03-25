package xyz.driver.core

import java.text.SimpleDateFormat
import java.util._
import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.util.Try

object time {

  // The most useful time units
  val Second  = 1000L
  val Seconds = Second
  val Minute  = 60 * Seconds
  val Minutes = Minute
  val Hour    = 60 * Minutes
  val Hours   = Hour
  val Day     = 24 * Hours
  val Days    = Day
  val Week    = 7 * Days
  val Weeks   = Week

  final case class Time(millis: Long) extends AnyVal {

    def isBefore(anotherTime: Time): Boolean = implicitly[Ordering[Time]].lt(this, anotherTime)

    def isAfter(anotherTime: Time): Boolean = implicitly[Ordering[Time]].gt(this, anotherTime)

    def advanceBy(duration: Duration): Time = Time(millis + duration.toMillis)

    def durationTo(anotherTime: Time): Duration = Duration.apply(anotherTime.millis - millis, TimeUnit.MILLISECONDS)

    def durationFrom(anotherTime: Time): Duration = Duration.apply(millis - anotherTime.millis, TimeUnit.MILLISECONDS)

    def toDate(timezone: TimeZone): date.Date = {
      val cal = Calendar.getInstance(timezone)
      cal.setTimeInMillis(millis)
      date.Date(cal.get(Calendar.YEAR), date.Month(cal.get(Calendar.MONTH)), cal.get(Calendar.DAY_OF_MONTH))
    }
  }

  /**
    * Encapsulates a time and timezone without a specific date.
    */
  final case class TimeOfDay(localTime: java.time.LocalTime, timeZone: TimeZone) {
    def isBefore(other: TimeOfDay, day: Int, month: Int, year: Int): Boolean = {
      getInstance(day, month, year).before(other.getInstance(day, month, year))
    }

    def isAfter(other: TimeOfDay, day: Int, month: Int, year: Int): Boolean = {
      getInstance(day, month, year).after(other.getInstance(day, month, year))
    }

    def sameTimeAs(other: TimeOfDay, day: Int, month: Int, year: Int): Boolean = {
      getInstance(day, month, year).equals(other.getInstance(day, month, year))
    }

    private def getInstance(day: Int, month: Int, year: Int): Calendar = {
      val cal = Calendar.getInstance(timeZone)
      cal.set(year, month, day, localTime.getHour, localTime.getMinute, localTime.getSecond)
      cal
    }
  }

  object TimeOfDay {
    def now(): TimeOfDay = {
      TimeOfDay(java.time.LocalTime.now(), TimeZone.getDefault)
    }

    /**
      * Throws when [s] is not parsable by [[java.time.LocalTime.parse]], uses default [[java.util.TimeZone]]
      */
    def apply(tz: TimeZone = TimeZone.getDefault)(s: String): TimeOfDay = {
      TimeOfDay(java.time.LocalTime.parse(s), tz)
    }

    def fromString(tz: TimeZone)(s: String): Option[TimeOfDay] = {
      val op = Try(java.time.LocalTime.parse(s)).toOption
      op.map(lt => TimeOfDay(lt, tz))
    }

    def fromStrings(zoneId: String)(s: String): Option[TimeOfDay] = {
      val op = Try(TimeZone.getTimeZone(zoneId)).toOption
      op.map(tz => TimeOfDay(tz)(s))
    }
  }

  implicit class TimeOfDayOps(tod: TimeOfDay) {
    private val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
      * Enforces the same formatting as expected by [[java.sql.Time]]
      * @return string formatted for `java.sql.Time`
      */
    def timeString: String = {
      tod.localTime.format(formatter)
    }

    /**
      * @return a string parsable by [[java.util.TimeZone]]
      */
    def timeZoneString: String = {
      tod.timeZone.getID
    }

    /**
      * @return this [[TimeOfDay]] as [[java.sql.Time]] object, [[java.sql.Time.valueOf]] will
      *         throw when the string is not valid, but this is protected by [[timeString]] method.
      */
    def toTime: java.sql.Time = {
      java.sql.Time.valueOf(tod.timeString)
    }
  }

  object Time {

    implicit def timeOrdering: Ordering[Time] = Ordering.by(_.millis)
  }

  final case class TimeRange(start: Time, end: Time) {
    def duration: Duration = FiniteDuration(end.millis - start.millis, MILLISECONDS)
  }

  def startOfMonth(time: Time) = {
    Time(make(new GregorianCalendar()) { cal =>
      cal.setTime(new Date(time.millis))
      cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH))
    }.getTime.getTime)
  }

  def textualDate(timezone: TimeZone)(time: Time): String =
    make(new SimpleDateFormat("MMMM d, yyyy"))(_.setTimeZone(timezone)).format(new Date(time.millis))

  def textualTime(timezone: TimeZone)(time: Time): String =
    make(new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a"))(_.setTimeZone(timezone)).format(new Date(time.millis))

  object provider {

    /**
      * Time providers are supplying code with current times
      * and are extremely useful for testing to check how system is going
      * to behave at specific moments in time.
      *
      * All the calls to receive current time must be made using time
      * provider injected to the caller.
      */
    trait TimeProvider {
      def currentTime(): Time
    }

    final class SystemTimeProvider extends TimeProvider {
      def currentTime() = Time(System.currentTimeMillis())
    }
    final val SystemTimeProvider = new SystemTimeProvider

    final class SpecificTimeProvider(time: Time) extends TimeProvider {
      def currentTime() = time
    }
  }
}
