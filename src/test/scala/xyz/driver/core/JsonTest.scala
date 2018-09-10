package xyz.driver.core

import java.net.InetAddress
import java.time.{Instant, LocalDate}

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher.Matched
import com.neovisionaries.i18n.{CountryCode, CurrencyCode}
import enumeratum._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineMV
import org.scalatest.{Inspectors, Matchers, WordSpec}
import spray.json._
import xyz.driver.core.TestTypes.CustomGADT
import xyz.driver.core.auth.AuthCredentials
import xyz.driver.core.domain.{Email, PhoneNumber}
import xyz.driver.core.json._
import xyz.driver.core.json.enumeratum.HasJsonFormat
import xyz.driver.core.tagging.Taggable
import xyz.driver.core.time.provider.SystemTimeProvider
import xyz.driver.core.time.{Time, TimeOfDay}

import scala.collection.immutable.IndexedSeq

class JsonTest extends WordSpec with Matchers with Inspectors {
  import DefaultJsonProtocol._

  "Json format for Id" should {
    "read and write correct JSON" in {

      val referenceId = Id[String]("1312-34A")

      val writtenJson = json.idFormat.write(referenceId)
      writtenJson.prettyPrint should be("\"1312-34A\"")

      val parsedId = json.idFormat.read(writtenJson)
      parsedId should be(referenceId)
    }
  }

  "Json format for @@" should {
    "read and write correct JSON" in {
      trait Irrelevant
      val reference = Id[JsonTest]("SomeID").tagged[Irrelevant]

      val format = json.taggedFormat[Id[JsonTest], Irrelevant]

      val writtenJson = format.write(reference)
      writtenJson shouldBe JsString("SomeID")

      val parsedId: Id[JsonTest] @@ Irrelevant = format.read(writtenJson)
      parsedId shouldBe reference
    }

    "read and write correct JSON when there's an implicit conversion defined" in {
      JsString(" some string ").convertTo[String @@ Trimmed] shouldBe "some string"

      val trimmed: String @@ Trimmed = "  some string  "
      trimmed.toJson shouldBe JsString("some string")
    }
  }

  "Json format for Name" should {
    "read and write correct JSON" in {

      val referenceName = Name[String]("Homer")

      val writtenJson = json.nameFormat.write(referenceName)
      writtenJson.prettyPrint should be("\"Homer\"")

      val parsedName = json.nameFormat.read(writtenJson)
      parsedName should be(referenceName)
    }
  }

  "Json format for NonEmptyName" should {
    "read and write correct JSON" in {

      val jsonFormat = json.nonEmptyNameFormat[String]

      val referenceNonEmptyName = NonEmptyName[String](refineMV[NonEmpty]("Homer"))

      val writtenJson = jsonFormat.write(referenceNonEmptyName)
      writtenJson.prettyPrint should be("\"Homer\"")

      val parsedNonEmptyName = jsonFormat.read(writtenJson)
      parsedNonEmptyName should be(referenceNonEmptyName)
    }
  }

  "Json format for Time" should {
    "read and write correct JSON" in {

      val referenceTime = new SystemTimeProvider().currentTime()

      val writtenJson = json.timeFormat.write(referenceTime)
      writtenJson.prettyPrint should be("{\n  \"timestamp\": " + referenceTime.millis + "\n}")

      val parsedTime = json.timeFormat.read(writtenJson)
      parsedTime should be(referenceTime)
    }

    "read from inputs compatible with Instant" in {
      val referenceTime = new SystemTimeProvider().currentTime()

      val jsons = Seq(JsNumber(referenceTime.millis), JsString(Instant.ofEpochMilli(referenceTime.millis).toString))

      forAll(jsons) { json =>
        json.convertTo[Time] shouldBe referenceTime
      }
    }
  }

  "Json format for TimeOfDay" should {
    "read and write correct JSON" in {
      val utcTimeZone        = java.util.TimeZone.getTimeZone("UTC")
      val referenceTimeOfDay = TimeOfDay.parseTimeString(utcTimeZone)("08:00:00")
      val writtenJson        = json.timeOfDayFormat.write(referenceTimeOfDay)
      writtenJson should be("""{"localTime":"08:00:00","timeZone":"UTC"}""".parseJson)
      val parsed = json.timeOfDayFormat.read(writtenJson)
      parsed should be(referenceTimeOfDay)
    }
  }

  "Json format for Date" should {
    "read and write correct JSON" in {
      import date._

      val referenceDate = Date(1941, Month.DECEMBER, 7)

      val writtenJson = json.dateFormat.write(referenceDate)
      writtenJson.prettyPrint should be("\"1941-12-07\"")

      val parsedDate = json.dateFormat.read(writtenJson)
      parsedDate should be(referenceDate)
    }
  }

  "Json format for java.time.Instant" should {

    val isoString = "2018-08-08T08:08:08.888Z"
    val instant   = Instant.parse(isoString)

    "read correct JSON when value is an epoch milli number" in {
      JsNumber(instant.toEpochMilli).convertTo[Instant] shouldBe instant
    }

    "read correct JSON when value is an ISO timestamp string" in {
      JsString(isoString).convertTo[Instant] shouldBe instant
    }

    "read correct JSON when value is an object with nested 'timestamp'/millis field" in {
      val json = JsObject(
        "timestamp" -> JsNumber(instant.toEpochMilli)
      )

      json.convertTo[Instant] shouldBe instant
    }

    "write correct JSON" in {
      instant.toJson shouldBe JsString(isoString)
    }
  }

  "Path matcher for Instant" should {

    val isoString = "2018-08-08T08:08:08.888Z"
    val instant   = Instant.parse(isoString)

    val matcher = PathMatcher("foo") / InstantInPath /

    "read instant from millis" in {
      matcher(Uri.Path("foo") / ("+" + instant.toEpochMilli) / "bar") shouldBe Matched(Uri.Path("bar"), Tuple1(instant))
    }

    "read instant from ISO timestamp string" in {
      matcher(Uri.Path("foo") / isoString / "bar") shouldBe Matched(Uri.Path("bar"), Tuple1(instant))
    }
  }

  "Json format for java.time.LocalDate" should {

    "read and write correct JSON" in {
      val dateString = "2018-08-08"
      val date       = LocalDate.parse(dateString)

      date.toJson shouldBe JsString(dateString)
      JsString(dateString).convertTo[LocalDate] shouldBe date
    }
  }

  "Json format for Revision" should {
    "read and write correct JSON" in {

      val referenceRevision = Revision[String]("037e2ec0-8901-44ac-8e53-6d39f6479db4")

      val writtenJson = json.revisionFormat.write(referenceRevision)
      writtenJson.prettyPrint should be("\"" + referenceRevision.id + "\"")

      val parsedRevision = json.revisionFormat.read(writtenJson)
      parsedRevision should be(referenceRevision)
    }
  }

  "Json format for Email" should {
    "read and write correct JSON" in {

      val referenceEmail = Email("test", "drivergrp.com")

      val writtenJson = json.emailFormat.write(referenceEmail)
      writtenJson should be("\"test@drivergrp.com\"".parseJson)

      val parsedEmail = json.emailFormat.read(writtenJson)
      parsedEmail should be(referenceEmail)
    }
  }

  "Json format for PhoneNumber" should {
    "read and write correct JSON" in {

      val referencePhoneNumber = PhoneNumber("1", "4243039608")

      val writtenJson = json.phoneNumberFormat.write(referencePhoneNumber)
      writtenJson should be("""{"countryCode":"1","number":"4243039608"}""".parseJson)

      val parsedPhoneNumber = json.phoneNumberFormat.read(writtenJson)
      parsedPhoneNumber should be(referencePhoneNumber)
    }

    "reject an invalid phone number" in {
      val phoneJson = """{"countryCode":"1","number":"111-111-1113"}""".parseJson

      intercept[DeserializationException] {
        json.phoneNumberFormat.read(phoneJson)
      }.getMessage shouldBe "Invalid phone number"
    }
  }

  "Json format for ADT mappings" should {
    "read and write correct JSON" in {

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
  }

  "Json format for Enums (external)" should {
    "read and write correct JSON" in {

      sealed trait MyEnum extends EnumEntry
      object MyEnum extends Enum[MyEnum] {
        case object Val1    extends MyEnum
        case object `Val 2` extends MyEnum
        case object `Val/3` extends MyEnum

        val values: IndexedSeq[MyEnum] = findValues
      }

      val format = new enumeratum.EnumJsonFormat(MyEnum)

      val referenceEnumValue1 = MyEnum.`Val 2`
      val referenceEnumValue2 = MyEnum.`Val/3`

      val writtenJson1 = format.write(referenceEnumValue1)
      writtenJson1 shouldBe JsString("Val 2")

      val writtenJson2 = format.write(referenceEnumValue2)
      writtenJson2 shouldBe JsString("Val/3")

      val parsedEnumValue1 = format.read(writtenJson1)
      val parsedEnumValue2 = format.read(writtenJson2)

      parsedEnumValue1 shouldBe referenceEnumValue1
      parsedEnumValue2 shouldBe referenceEnumValue2

      intercept[DeserializationException] {
        format.read(JsString("Val4"))
      }.getMessage shouldBe "Unexpected value Val4. Expected one of: [Val1, Val 2, Val/3]"
    }
  }

  "Json format for Enums (automatic)" should {
    "read and write correct JSON and not require import" in {

      sealed trait MyEnum extends EnumEntry
      object MyEnum extends Enum[MyEnum] with HasJsonFormat[MyEnum] {
        case object Val1    extends MyEnum
        case object `Val 2` extends MyEnum
        case object `Val/3` extends MyEnum

        val values: IndexedSeq[MyEnum] = findValues
      }

      val referenceEnumValue1: MyEnum = MyEnum.`Val 2`
      val referenceEnumValue2: MyEnum = MyEnum.`Val/3`

      val writtenJson1 = referenceEnumValue1.toJson
      writtenJson1 shouldBe JsString("Val 2")

      val writtenJson2 = referenceEnumValue2.toJson
      writtenJson2 shouldBe JsString("Val/3")

      import spray.json._

      val parsedEnumValue1 = writtenJson1.prettyPrint.parseJson.convertTo[MyEnum]
      val parsedEnumValue2 = writtenJson2.prettyPrint.parseJson.convertTo[MyEnum]

      parsedEnumValue1 should be(referenceEnumValue1)
      parsedEnumValue2 should be(referenceEnumValue2)

      intercept[DeserializationException] {
        JsString("Val4").convertTo[MyEnum]
      }.getMessage shouldBe "Unexpected value Val4. Expected one of: [Val1, Val 2, Val/3]"
    }
  }

  // Should be defined outside of case to have a TypeTag
  case class CustomWrapperClass(value: Int)

  "Json format for Value classes" should {
    "read and write correct JSON" in {

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
  }

  "Json format for classes GADT" should {
    "read and write correct JSON" in {

      import CustomGADT._
      import DefaultJsonProtocol._
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
  }

  "Json format for a Refined value" should {
    "read and write correct JSON" in {

      val jsonFormat = json.refinedJsonFormat[Int, Positive]

      val referenceRefinedNumber = refineMV[Positive](42)

      val writtenJson = jsonFormat.write(referenceRefinedNumber)
      writtenJson should be("42".parseJson)

      val parsedRefinedNumber = jsonFormat.read(writtenJson)
      parsedRefinedNumber should be(referenceRefinedNumber)
    }
  }

  "InetAddress format" should {
    "read and write correct JSON" in {
      val address = InetAddress.getByName("127.0.0.1")
      val json    = inetAddressFormat.write(address)

      json shouldBe JsString("127.0.0.1")

      val parsed = inetAddressFormat.read(json)
      parsed shouldBe address
    }

    "throw a DeserializationException for an invalid IP Address" in {
      assertThrows[DeserializationException] {
        val invalidAddress = JsString("foobar:")
        inetAddressFormat.read(invalidAddress)
      }
    }
  }

  "AuthCredentials format" should {
    "read and write correct JSON" in {
      val email    = Email("someone", "noehere.com")
      val phoneId  = PhoneNumber.parse("1 207 8675309")
      val password = "nopassword"

      phoneId.isDefined should be(true) // test this real quick

      val emailAuth = AuthCredentials(email.toString, password)
      val pnAuth    = AuthCredentials(phoneId.get.toString, password)

      val emailWritten = authCredentialsFormat.write(emailAuth)
      emailWritten should be("""{"identifier":"someone@noehere.com","password":"nopassword"}""".parseJson)

      val phoneWritten = authCredentialsFormat.write(pnAuth)
      phoneWritten should be("""{"identifier":"+1 2078675309","password":"nopassword"}""".parseJson)

      val identifierEmailParsed =
        authCredentialsFormat.read("""{"identifier":"someone@nowhere.com","password":"nopassword"}""".parseJson)
      var written = authCredentialsFormat.write(identifierEmailParsed)
      written should be("{\"identifier\":\"someone@nowhere.com\",\"password\":\"nopassword\"}".parseJson)

      val emailEmailParsed =
        authCredentialsFormat.read("""{"email":"someone@nowhere.com","password":"nopassword"}""".parseJson)
      written = authCredentialsFormat.write(emailEmailParsed)
      written should be("{\"identifier\":\"someone@nowhere.com\",\"password\":\"nopassword\"}".parseJson)

    }
  }

  "CountryCode format" should {
    "read and write correct JSON" in {
      val samples = Seq(
        "US" -> CountryCode.US,
        "CN" -> CountryCode.CN,
        "AT" -> CountryCode.AT
      )

      forAll(samples) {
        case (serialized, enumValue) =>
          countryCodeFormat.write(enumValue) shouldBe JsString(serialized)
          countryCodeFormat.read(JsString(serialized)) shouldBe enumValue
      }
    }
  }

  "CurrencyCode format" should {
    "read and write correct JSON" in {
      val samples = Seq(
        "USD" -> CurrencyCode.USD,
        "CNY" -> CurrencyCode.CNY,
        "EUR" -> CurrencyCode.EUR
      )

      forAll(samples) {
        case (serialized, enumValue) =>
          currencyCodeFormat.write(enumValue) shouldBe JsString(serialized)
          currencyCodeFormat.read(JsString(serialized)) shouldBe enumValue
      }
    }
  }

}
