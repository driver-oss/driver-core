package xyz.driver.core

import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.domain.PhoneNumber

class PhoneNumberTest extends FlatSpec with Matchers {

  "PhoneNumber.parse" should "recognize US numbers in international format, ignoring non-digits" in {
    // format: off
    val numbers = List(
      "+18005252225",
      "+1 800 525 2225",
      "+1 (800) 525-2225",
      "+1.800.525.2225")
    // format: on

    val parsed = numbers.flatMap(PhoneNumber.parse)

    parsed should have size numbers.size
    parsed should contain only PhoneNumber("1", "8005252225")
  }

  it should "recognize US numbers without the plus sign" in {
    PhoneNumber.parse("18005252225") shouldBe Some(PhoneNumber("1", "8005252225"))
  }

  it should "recognize US numbers without country code" in {
    // format: off
    val numbers = List(
      "8005252225",
      "800 525 2225",
      "(800) 525-2225",
      "800.525.2225")
    // format: on

    val parsed = numbers.flatMap(PhoneNumber.parse)

    parsed should have size numbers.size
    parsed should contain only PhoneNumber("1", "8005252225")
  }

  it should "recognize CN numbers in international format" in {
    PhoneNumber.parse("+868005252225") shouldBe Some(PhoneNumber("86", "8005252225"))
    PhoneNumber.parse("+86 134 52 52 2256") shouldBe Some(PhoneNumber("86", "13452522256"))
  }

  it should "parse numbers with extensions in different formats" in {
    // format: off
    val numbers = List(
      "+1 800 525 22 25 x23",
      "+18005252225 ext. 23",
      "+18005252225,23"
    )
    // format: on

    val parsed = numbers.flatMap(PhoneNumber.parse)

    parsed should have size numbers.size
    parsed should contain only PhoneNumber("1", "8005252225", Some("23"))
  }

  it should "return None on numbers that are shorter than the minimum number of digits for the country (i.e. US - 10, AR - 11)" in {
    withClue("US and CN numbers are 10 digits - 9 digit (and shorter) numbers should not fit") {
      // format: off
      val numbers = List(
        "+1 800 525-222",
        "+1 800 525-2",
        "+86 800 525-222",
        "+86 800 525-2")
      // format: on

      numbers.flatMap(PhoneNumber.parse) shouldBe empty
    }

    withClue("Argentinian numbers are 11 digits (when prefixed with 0) - 10 digit numbers shouldn't fit") {
      // format: off
      val numbers = List(
        "+54 011 525-22256",
        "+54 011 525-2225",
        "+54 011 525-222")
      // format: on

      numbers.flatMap(PhoneNumber.parse) should contain theSameElementsAs List(PhoneNumber("54", "1152522256"))
    }
  }

  it should "return None on numbers that are longer than the maximum number of digits for the country (i.e. DK - 8, CN - 11)" in {
    val numbers = List("+45 27 45 25 22", "+45 135 525 223", "+86 134 525 22256", "+86 135 525 22256 7")

    numbers.flatMap(PhoneNumber.parse) should contain theSameElementsAs
      List(PhoneNumber("45", "27452522"), PhoneNumber("86", "13452522256"))
  }

  "PhoneNumber.toCompactString/toE164String" should "produce phone number in international format without whitespaces" in {
    PhoneNumber.parse("+1 800 5252225").get.toCompactString shouldBe "+18005252225"
    PhoneNumber.parse("+1 800 5252225").get.toE164String shouldBe "+18005252225"

    PhoneNumber.parse("+1 800 5252225 x23").get.toCompactString shouldBe "+18005252225;ext=23"
    PhoneNumber.parse("+1 800 5252225 x23").get.toE164String shouldBe "+18005252225;ext=23"
  }

  "PhoneNumber.toHumanReadableString" should "produce nice readable result for different countries" in {
    PhoneNumber.parse("+14154234567").get.toHumanReadableString shouldBe "+1 415-423-4567"
    PhoneNumber.parse("+14154234567,23").get.toHumanReadableString shouldBe "+1 415-423-4567 ext. 23"

    PhoneNumber.parse("+78005252225").get.toHumanReadableString shouldBe "+7 800 525-22-25"

    PhoneNumber.parse("+41219437898").get.toHumanReadableString shouldBe "+41 21 943 78 98"
  }

  it should "throw an IllegalArgumentException if the PhoneNumber object is not parsable/valid" in {
    intercept[IllegalStateException] {
      PhoneNumber("+123", "1238123120938120938").toHumanReadableString
    }.getMessage should include("+123 1238123120938120938 is not a valid number")
  }

}
