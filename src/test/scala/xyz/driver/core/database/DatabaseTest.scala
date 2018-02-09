package xyz.driver.core.database

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.Checkers
import xyz.driver.core.rest.errors.DatabaseException

class DatabaseTest extends FlatSpec with Matchers with Checkers {
  import xyz.driver.core.generators._
  "Date SQL converter" should "correctly convert back and forth to SQL dates" in {
    for (date <- 1 to 100 map (_ => nextDate())) {
      sqlDateToDate(dateToSqlDate(date)) should be(date)
    }
  }

  "Converter helper methods" should "work correctly" in {
    object TestConverter extends Converters

    val validLength                       = nextInt(10)
    val valid                             = nextToken(validLength)
    val invalid                           = nextToken(validLength + nextInt(10, 1))
    def mapper(s: String): Option[String] = if (s.length == validLength) Some(s) else None

    TestConverter.fromStringOrThrow(valid, mapper, valid) should be(valid)
    TestConverter.expectValid(mapper, valid) should be(valid)
    an[DatabaseException] should be thrownBy TestConverter.fromStringOrThrow(invalid, mapper, invalid)
  }

}
