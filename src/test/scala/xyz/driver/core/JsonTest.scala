package xyz.driver.core

import java.net.InetAddress

import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineMV
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.time.provider.SystemTimeProvider
import spray.json._
import xyz.driver.core.TestTypes.CustomGADT
import xyz.driver.core.domain.{Email, PhoneNumber}
import xyz.driver.core.time.Time

class JsonTest extends FlatSpec with Matchers with CoreJsonFormats {

  "Json format for Id" should "read and write correct JSON" in {

    val referenceId = Id[String]("1312-34A")

    val writtenJson = referenceId.toJson
    writtenJson.prettyPrint should be("\"1312-34A\"")

    val parsedId = writtenJson.convertTo[Id[String]]
    parsedId should be(referenceId)
  }

  "Json format for Name" should "read and write correct JSON" in {

    val referenceName = Name[String]("Homer")

    val writtenJson = referenceName.toJson
    writtenJson.prettyPrint should be("\"Homer\"")

    val parsedName = writtenJson.convertTo[Name[String]]
    parsedName should be(referenceName)
  }

  "Json format for NonEmptyName" should "read and write correct JSON" in {

    val jsonFormat = nonEmptyNameFormat[String]

    val referenceNonEmptyName = NonEmptyName[String](refineMV[NonEmpty]("Homer"))

    val writtenJson = jsonFormat.write(referenceNonEmptyName)
    writtenJson.prettyPrint should be("\"Homer\"")

    val parsedNonEmptyName = jsonFormat.read(writtenJson)
    parsedNonEmptyName should be(referenceNonEmptyName)
  }

  "Json format for Time" should "read and write correct JSON" in {

    val referenceTime = new SystemTimeProvider().currentTime()

    val writtenJson = referenceTime.toJson
    writtenJson.prettyPrint should be("{\n  \"timestamp\": " + referenceTime.millis + "\n}")

    val parsedTime = writtenJson.convertTo[Time]
    parsedTime should be(referenceTime)
  }

  "Json format for Date" should "read and write correct JSON" in {
    import date._

    val referenceDate = Date(1941, Month.DECEMBER, 7)

    val writtenJson = referenceDate.toJson
    writtenJson.prettyPrint should be("\"1941-12-07\"")

    val parsedDate = writtenJson.convertTo[Date]
    parsedDate should be(referenceDate)
  }

  "Json format for Revision" should "read and write correct JSON" in {

    val referenceRevision = Revision[String]("037e2ec0-8901-44ac-8e53-6d39f6479db4")

    val writtenJson = referenceRevision.toJson
    writtenJson.prettyPrint should be("\"" + referenceRevision.id + "\"")

    val parsedRevision = writtenJson.convertTo[Revision[String]]
    parsedRevision should be(referenceRevision)
  }

  "Json format for Email" should "read and write correct JSON" in {

    val referenceEmail = Email("test", "drivergrp.com")

    val writtenJson = referenceEmail.toJson
    writtenJson should be("\"test@drivergrp.com\"".parseJson)

    val parsedEmail = writtenJson.convertTo[Email]
    parsedEmail should be(referenceEmail)
  }

  "Json format for PhoneNumber" should "read and write correct JSON" in {

    val referencePhoneNumber = PhoneNumber("1", "4243039608")

    val writtenJson = referencePhoneNumber.toJson
    writtenJson should be("""{"countryCode":"1","number":"4243039608"}""".parseJson)

    val parsedPhoneNumber = writtenJson.convertTo[PhoneNumber]
    parsedPhoneNumber should be(referencePhoneNumber)
  }

  "Json format for Enums" should "read and write correct JSON" in {

    sealed trait EnumVal
    case object Val1 extends EnumVal
    case object Val2 extends EnumVal
    case object Val3 extends EnumVal

    val format = new EnumJsonFormat[EnumVal]("a" -> Val1, "b" -> Val2, "c" -> Val3)

    val referenceEnumValue1 = Val2
    val referenceEnumValue2 = Val3

    val writtenJson1 = format.write(referenceEnumValue1)
    writtenJson1.prettyPrint should be("\"b\"")

    val writtenJson2 = format.write(referenceEnumValue2)
    writtenJson2.prettyPrint should be("\"c\"")

    val parsedEnumValue1 = format.read(writtenJson1)
    val parsedEnumValue2 = format.read(writtenJson2)

    parsedEnumValue1 should be(referenceEnumValue1)
    parsedEnumValue2 should be(referenceEnumValue2)
  }

  // Should be defined outside of case to have a TypeTag
  case class CustomWrapperClass(value: Int)

  "Json format for Value classes" should "read and write correct JSON" in {

    val format = new ValueClassFormat[CustomWrapperClass](v => BigDecimal(v.value), d => CustomWrapperClass(d.toInt))

    val referenceValue1 = CustomWrapperClass(-2)
    val referenceValue2 = CustomWrapperClass(10)

    val writtenJson1 = format.write(referenceValue1)
    writtenJson1.prettyPrint should be("-2")

    val writtenJson2 = format.write(referenceValue2)
    writtenJson2.prettyPrint should be("10")

    val parsedValue1 = format.read(writtenJson1)
    val parsedValue2 = format.read(writtenJson2)

    parsedValue1 should be(referenceValue1)
    parsedValue2 should be(referenceValue2)
  }

  "Json format for classes GADT" should "read and write correct JSON" in {

    import CustomGADT._
    implicit val case1Format = jsonFormat1(GadtCase1)
    implicit val case2Format = jsonFormat1(GadtCase2)
    implicit val case3Format = jsonFormat1(GadtCase3)

    val format = GadtJsonFormat.create[CustomGADT]("gadtTypeField") {
      case _: CustomGADT.GadtCase1 => "case1"
      case _: CustomGADT.GadtCase2 => "case2"
      case _: CustomGADT.GadtCase3 => "case3"
    } {
      case "case1" => case1Format
      case "case2" => case2Format
      case "case3" => case3Format
    }

    val referenceValue1 = CustomGADT.GadtCase1("4")
    val referenceValue2 = CustomGADT.GadtCase2("Hi!")

    val writtenJson1 = format.write(referenceValue1)
    writtenJson1 should be("{\n \"field\": \"4\",\n\"gadtTypeField\": \"case1\"\n}".parseJson)

    val writtenJson2 = format.write(referenceValue2)
    writtenJson2 should be("{\"field\":\"Hi!\",\"gadtTypeField\":\"case2\"}".parseJson)

    val parsedValue1 = format.read(writtenJson1)
    val parsedValue2 = format.read(writtenJson2)

    parsedValue1 should be(referenceValue1)
    parsedValue2 should be(referenceValue2)
  }

  "Json format for a Refined value" should "read and write correct JSON" in {

    val jsonFormat = refinedJsonFormat[Int, Positive]

    val referenceRefinedNumber = refineMV[Positive](42)

    val writtenJson = jsonFormat.write(referenceRefinedNumber)
    writtenJson should be("42".parseJson)

    val parsedRefinedNumber = jsonFormat.read(writtenJson)
    parsedRefinedNumber should be(referenceRefinedNumber)
  }

  "InetAddress format" should "read and write correct JSON" in {
    val address = InetAddress.getByName("127.0.0.1")
    val json    = inetAddressFormat.write(address)

    json shouldBe JsString("127.0.0.1")

    val parsed = inetAddressFormat.read(json)
    parsed shouldBe address
  }

  it should "throw a DeserializationException for an invalid IP Address" in {
    assertThrows[DeserializationException] {
      val invalidAddress = JsString("foobar")
      inetAddressFormat.read(invalidAddress)
    }
  }
}
