package com.drivergrp.core

import java.text.SimpleDateFormat
import java.util.{Calendar, Date, GregorianCalendar}

import scala.concurrent.duration.Duration

object time {

  // The most useful time units
  val Second = 1000L
  val Seconds = Second
  val Minute = 60 * Seconds
  val Minutes = Minute
  val Hour = 60 * Minutes
  val Hours = Hour
  val Day = 24 * Hours
  val Days = Day
  val Week = 7 * Days
  val Weeks = Week


  case class Time(millis: Long) extends AnyVal {

    def isBefore(anotherTime: Time): Boolean = millis < anotherTime.millis

    def isAfter(anotherTime: Time): Boolean = millis > anotherTime.millis

    def advanceBy(duration: Duration): Time = Time(millis + duration.length)
  }

  case class TimeRange(start: Time, end: Time)

  implicit def timeOrdering: Ordering[Time] = Ordering.by(_.millis)


  def startOfMonth(time: Time) = {
    make(new GregorianCalendar()) { cal =>
      cal.setTime(new Date(time.millis))
      cal.set(Calendar.DAY_OF_MONTH, cal.getActualMinimum(Calendar.DAY_OF_MONTH))
      Time(cal.getTime.getTime)
    }
  }

  def textualDate(time: Time): String =
    new SimpleDateFormat("MMMM d, yyyy").format(new Date(time.millis))

  def textualTime(time: Time): String =
    new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a").format(new Date(time.millis))


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
