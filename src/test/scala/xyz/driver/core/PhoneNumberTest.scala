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

  "PhoneNumber.value" should "produce phone number in international format without whitespaces" in {
    PhoneNumber.parse("+1 800 5252225").get.value shouldBe "+18005252225"
  }

}
