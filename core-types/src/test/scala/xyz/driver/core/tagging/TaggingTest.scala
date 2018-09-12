package xyz.driver.core.tagging

import org.scalatest.{Matchers, WordSpec}
import xyz.driver.core.{@@, Name}

/**
  * @author sergey
  * @since 9/11/18
  */
class TaggingTest extends WordSpec with Matchers {

  "@@ Trimmed" should {
    "produce values transparently from Strings and Names (by default)" in {
      val s: String @@ Trimmed    = " trimmed "
      val n: Name[Int] @@ Trimmed = Name(" trimmed ")

      s shouldBe "trimmed"
      n shouldBe Name[Int]("trimmed")
    }

    "produce values transparently from values that have an implicit conversion defined" in {
      import scala.language.implicitConversions
      implicit def stringSeq2Trimmed(stringSeq: Seq[String]): Seq[String] @@ Trimmed =
        stringSeq.map(_.trim()).tagged[Trimmed]

      val strings: Seq[String] @@ Trimmed = Seq(" trimmed1 ", " trimmed2 ")
      strings shouldBe Seq("trimmed1", "trimmed2")
    }

    "produce values transparently from Options of values that have Trimmed implicits" in {
      val maybeStringDirect: Option[String @@ Trimmed]  = Some(" trimmed ")
      val maybeStringFromMap: Option[String @@ Trimmed] = Map("s" -> " trimmed ").get("s")

      val maybeNameDirect: Option[Name[Int] @@ Trimmed]  = Some(Name(" trimmed "))
      val maybeNameFromMap: Option[Name[Int] @@ Trimmed] = Map("s" -> Name[Int](" trimmed ")).get("s")

      maybeStringDirect shouldBe Some("trimmed")
      maybeStringFromMap shouldBe Some("trimmed")
      maybeNameDirect shouldBe Some(Name[Int]("trimmed"))
      maybeNameFromMap shouldBe Some(Name[Int]("trimmed"))
    }

    "produce values transparently from collections of values that have Trimmed implicits" in {
      val strings = Seq("s" -> " trimmed1 ", "s" -> " trimmed2 ")
      val names = strings.map {
        case (k, v) => k -> Name[Int](v)
      }

      val trimmedStrings: Seq[String @@ Trimmed]  = strings.groupBy(_._1)("s").map(_._2)
      val trimmedNames: Seq[Name[Int] @@ Trimmed] = names.groupBy(_._1)("s").map(_._2)

      trimmedStrings shouldBe Seq("trimmed1", "trimmed2")
      trimmedNames shouldBe Seq("trimmed1", "trimmed2").map(Name[Int])
    }

    "have Ordering" in {
      val names: Seq[Name[Int] @@ Trimmed] = Seq(" 2 ", " 1 ", "3").map(Name[Int])

      names.sorted should contain inOrderOnly (Name("1"), Name("2"), Name("3"))
    }
  }

}
