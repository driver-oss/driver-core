package xyz.driver.core.database

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers

class DatabaseTest extends FlatSpec with Matchers with Checkers {
  "Date SQL converter" should "correctly convert back and forth to SQL dates" in {
    import xyz.driver.core.generators.nextDate

    for (date <- 1 to 100 map (_ => nextDate())) {
      sqlDateToDate(dateToSqlDate(date)) should be(date)
    }
  }
}
