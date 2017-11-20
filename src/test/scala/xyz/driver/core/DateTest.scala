package xyz.driver.core

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.Checkers
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.date.Date

class DateTest extends FlatSpec with Matchers with Checkers {
  val dateGenerator = for {
    year  <- Gen.choose(0, 3000)
    month <- Gen.choose(0, 11)
    day   <- Gen.choose(1, 31)
  } yield Date(year, date.Month(month), day)
  implicit val arbitraryDate = Arbitrary[Date](dateGenerator)

  "Date" should "correctly convert to and from String" in {

    import xyz.driver.core.generators.nextDate
    import date._

    for (date <- 1 to 100 map (_ => nextDate())) {
      Some(date) should be(Date.fromString(date.toString))
    }
  }

  it should "have ordering defined correctly" in {
    Seq(
      Date.fromString("2013-05-10"),
      Date.fromString("2020-02-15"),
      Date.fromString("2017-03-05"),
      Date.fromString("2013-05-12")).sorted should
      contain theSameElementsInOrderAs Seq(
      Date.fromString("2013-05-10"),
      Date.fromString("2013-05-12"),
      Date.fromString("2017-03-05"),
      Date.fromString("2020-02-15"))

    check { dates: List[Date] =>
      dates.sorted.sliding(2).filter(_.size == 2).forall {
        case Seq(a, b) =>
          if (a.year == b.year) {
            if (a.month == b.month) {
              a.day <= b.day
            } else {
              a.month < b.month
            }
          } else {
            a.year < b.year
          }
      }
    }
  }
}
