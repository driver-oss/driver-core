package com.drivergrp.core

import java.util.TimeZone

import com.drivergrp.core.time.{Time, _}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._

class TimeTest extends FlatSpec with Matchers {

  "Time" should "have correct methods to compare" in {

    Time(234L).isAfter(Time(123L)) should be(true)
    Time(123L).isAfter(Time(123L)) should be(false)
    Time(123L).isAfter(Time(234L)) should be(false)

    Time(234L).isBefore(Time(123L)) should be(false)
    Time(123L).isBefore(Time(123L)) should be(false)
    Time(123L).isBefore(Time(234L)) should be(true)
  }

  it should "not modify time" in {

    Time(234L).millis should be(234L)
  }

  it should "support arithmetic with scala.concurrent.duration" in {

    Time(123L).advanceBy(0 minutes).millis should be(123L)
    Time(123L).advanceBy(1 second).millis should be(123L + Second)
    Time(123L).advanceBy(4 days).millis should be(123L + 4 * Days)
  }

  it should "have ordering defined correctly" in {

    Seq(Time(321L), Time(123L), Time(231L)).sorted should
    contain theSameElementsInOrderAs Seq(Time(123L), Time(231L), Time(321L))
  }

  it should "reset to the start of the period, e.g. month" in {

    startOfMonth(Time(1468937089834L)) should be(Time(1467381889834L))
    startOfMonth(Time(1467381889834L)) should be(Time(1467381889834L)) // idempotent
  }

  it should "have correct textual representations" in {

    textualDate(TimeZone.getTimeZone("EDT"))(Time(1468937089834L)) should be("July 19, 2016")
    textualTime(TimeZone.getTimeZone("PDT"))(Time(1468937089834L)) should be("Jul 19, 2016 02:04:49 PM")
  }

  "TimeRange" should "have duration defined as a difference of start and end times" in {

    TimeRange(Time(321L), Time(432L)).duration should be(111.milliseconds)
    TimeRange(Time(432L), Time(321L)).duration should be((-111).milliseconds)
    TimeRange(Time(333L), Time(333L)).duration should be(0.milliseconds)
  }
}
