package xyz.driver.core

import java.util.TimeZone

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop.BooleanOperators
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.date.Month
import xyz.driver.core.time.{Time, _}

import scala.concurrent.duration._

class TimeTest extends FlatSpec with Matchers with Checkers {

  implicit val arbitraryDuration = Arbitrary[Duration](Gen.chooseNum(0L, 9999999999L).map(_.milliseconds))
  implicit val arbitraryTime     = Arbitrary[Time](Gen.chooseNum(0L, 9999999999L).map(millis => Time(millis)))

  "Time" should "have correct methods to compare" in {

    Time(234L).isAfter(Time(123L)) should be(true)
    Time(123L).isAfter(Time(123L)) should be(false)
    Time(123L).isAfter(Time(234L)) should be(false)

    check((a: Time, b: Time) => (a.millis > b.millis) ==> a.isAfter(b))

    Time(234L).isBefore(Time(123L)) should be(false)
    Time(123L).isBefore(Time(123L)) should be(false)
    Time(123L).isBefore(Time(234L)) should be(true)

    check { (a: Time, b: Time) =>
      (a.millis < b.millis) ==> a.isBefore(b)
    }
  }

  it should "not modify time" in {

    Time(234L).millis should be(234L)

    check { millis: Long =>
      Time(millis).millis == millis
    }
  }

  it should "support arithmetic with scala.concurrent.duration" in {

    Time(123L).advanceBy(0 minutes).millis should be(123L)
    Time(123L).advanceBy(1 second).millis should be(123L + Second)
    Time(123L).advanceBy(4 days).millis should be(123L + 4 * Days)

    check { (time: Time, duration: Duration) =>
      time.advanceBy(duration).millis == (time.millis + duration.toMillis)
    }
  }

  it should "have ordering defined correctly" in {

    Seq(Time(321L), Time(123L), Time(231L)).sorted should
      contain theSameElementsInOrderAs Seq(Time(123L), Time(231L), Time(321L))

    check { times: List[Time] =>
      times.sorted.sliding(2).filter(_.size == 2).forall {
        case Seq(a, b) =>
          a.millis <= b.millis
      }
    }
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

  "Time" should "use TimeZone correctly when converting to Date" in {

    val EST = java.util.TimeZone.getTimeZone("EST")
    val PST = java.util.TimeZone.getTimeZone("PST")

    val timestamp = {
      import java.util.Calendar
      val cal = Calendar.getInstance(EST)
      cal.set(Calendar.HOUR_OF_DAY, 1)
      Time(cal.getTime().getTime())
    }

    textualDate(EST)(timestamp) should not be textualDate(PST)(timestamp)
    timestamp.toDate(EST) should not be timestamp.toDate(PST)
  }

  "TimeOfDay" should "be created from valid strings and convert to java.sql.Time" in {
    val s               = "07:30:45"
    val defaultTimeZone = TimeZone.getDefault()
    val todFactory      = TimeOfDay.parseTimeString(defaultTimeZone)(_)
    val tod             = todFactory(s)
    tod.timeString shouldBe s
    tod.timeZoneString shouldBe defaultTimeZone.getID
    val sqlTime = tod.toTime
    sqlTime.toLocalTime shouldBe tod.localTime
    a[java.time.format.DateTimeParseException] should be thrownBy {
      val illegal = "7:15"
      todFactory(illegal)
    }
  }

  "TimeOfDay" should "have correct temporal relationships" in {
    val s             = "07:30:45"
    val t             = "09:30:45"
    val pst           = TimeZone.getTimeZone("America/Los_Angeles")
    val est           = TimeZone.getTimeZone("America/New_York")
    val pstTodFactory = TimeOfDay.parseTimeString(pst)(_)
    val estTodFactory = TimeOfDay.parseTimeString(est)(_)
    val day           = 1
    val month         = Month.JANUARY
    val year          = 2018
    val sTodPst       = pstTodFactory(s)
    val sTodPst2      = pstTodFactory(s)
    val tTodPst       = pstTodFactory(t)
    val tTodEst       = estTodFactory(t)
    sTodPst.isBefore(tTodPst, day, month, year) shouldBe true
    tTodPst.isAfter(sTodPst, day, month, year) shouldBe true
    tTodEst.isBefore(sTodPst, day, month, year) shouldBe true
    sTodPst.sameTimeAs(sTodPst2, day, month, year) shouldBe true
  }
}
