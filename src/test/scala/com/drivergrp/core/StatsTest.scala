package com.drivergrp.core

import com.drivergrp.core.logging.Logger
import com.drivergrp.core.stats.LogStats
import com.drivergrp.core.time.{Time, TimeRange}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito._

class StatsTest extends FlatSpec with Matchers with MockitoSugar {

  "Stats" should "format and store all recorded data" in {

    val log   = mock[Logger]
    val stats = new LogStats(log)

    stats.recordStats(Seq(), TimeRange(Time(2L), Time(5L)), BigDecimal(123.324))
    verify(log).audit(s"(2-5)=123.324")

    stats.recordStats("stat", TimeRange(Time(5L), Time(5L)), BigDecimal(333L))
    verify(log).audit(s"stat(5-5)=333")

    stats.recordStats("stat", Time(934L), 123)
    verify(log).audit(s"stat(934-934)=123")

    stats.recordStats("stat", Time(0L), 123)
    verify(log).audit(s"stat(0-0)=123")
  }

  it should "format BigDecimal with all precision digits" in {

    val log   = mock[Logger]
    val stats = new LogStats(log)

    stats.recordStats(Seq("root", "group", "stat", "substat"),
                      TimeRange(Time(1467381889834L), Time(1468937089834L)),
                      BigDecimal(3.333333333333333))
    verify(log).audit(s"root.group.stat.substat(1467381889834-1468937089834)=3.333333333333333")

    stats.recordStats("stat", Time(1233L), BigDecimal(0.00000000000000000000001))
    verify(log).audit(s"stat(1233-1233)=0.000000000000000000000010")
  }
}
