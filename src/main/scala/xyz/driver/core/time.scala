package xyz.driver.core

import java.text.SimpleDateFormat
import java.util._

import scala.concurrent.duration._

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

    def isBefore(anotherTime: Time): Boolean = millis < anotherTime.millis

    def isAfter(anotherTime: Time): Boolean = millis > anotherTime.millis

    def advanceBy(duration: Duration): Time = Time(millis + duration.toMillis)
  }

  final case class TimeRange(start: Time, end: Time) {
    def duration: Duration = FiniteDuration(end.millis - start.millis, MILLISECONDS)
  }

  implicit def timeOrdering: Ordering[Time] = Ordering.by(_.millis)

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

    final class SpecificTimeProvider(time: Time) extends TimeProvider {
      def currentTime() = time
    }
  }
}
