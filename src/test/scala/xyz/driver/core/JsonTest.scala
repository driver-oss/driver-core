package xyz.driver.core

import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.json.{EnumJsonFormat, GadtJsonFormat, ValueClassFormat}
import xyz.driver.core.revision.Revision
import xyz.driver.core.time.provider.SystemTimeProvider
import spray.json._
import xyz.driver.core.TestTypes.CustomGADT

class JsonTest extends FlatSpec with Matchers {

  "Json format for Id" should "read and write correct JSON" in {

    val referenceId = Id[String]("1312-34A")

    val writtenJson = json.idFormat.write(referenceId)
    writtenJson.prettyPrint should be("\"1312-34A\"")

    val parsedId = json.idFormat.read(writtenJson)
    parsedId should be(referenceId)
  }

  "Json format for Name" should "read and write correct JSON" in {

    val referenceName = Name[String]("Homer")

    val writtenJson = json.nameFormat.write(referenceName)
    writtenJson.prettyPrint should be("\"Homer\"")

    val parsedName = json.nameFormat.read(writtenJson)
    parsedName should be(referenceName)
  }

  "Json format for Time" should "read and write correct JSON" in {

    val referenceTime = new SystemTimeProvider().currentTime()

    val writtenJson = json.timeFormat.write(referenceTime)
    writtenJson.prettyPrint should be("{\n  \"timestamp\": " + referenceTime.millis + "\n}")

    val parsedTime = json.timeFormat.read(writtenJson)
    parsedTime should be(referenceTime)
  }

  "Json format for Date" should "read and write correct JSON" in {
    import date._

    val referenceDate = Date(1941, Month.DECEMBER, 7)

    val writtenJson = json.dateFormat.write(referenceDate)
    writtenJson.prettyPrint should be("""|{
                                         |  "year": 1941,
                                         |  "month": 11,
                                         |  "day": 7
                                         |}""".stripMargin)

    val parsedDate = json.dateFormat.read(writtenJson)
    parsedDate should be(referenceDate)
  }

  "Json format for Revision" should "read and write correct JSON" in {

    val referenceRevision = Revision[String]("037e2ec0-8901-44ac-8e53-6d39f6479db4")

    val writtenJson = json.revisionFormat.write(referenceRevision)
    writtenJson.prettyPrint should be("\"" + referenceRevision.id + "\"")

    val parsedRevision = json.revisionFormat.read(writtenJson)
    parsedRevision should be(referenceRevision)
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
    import DefaultJsonProtocol._
    implicit val case1Format = jsonFormat1(GadtCase1)
    implicit val case2Format = jsonFormat1(GadtCase2)
    implicit val case3Format = jsonFormat1(GadtCase3)

    val format = GadtJsonFormat.create[CustomGADT]("gadtTypeField") {
      case t1: CustomGADT.GadtCase1 => "case1"
      case t2: CustomGADT.GadtCase2 => "case2"
      case t3: CustomGADT.GadtCase3 => "case3"
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
