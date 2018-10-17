package xyz.driver.core

import java.io.ByteArrayOutputStream
import java.util.UUID

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class CoreTest extends FlatSpec with Matchers with MockitoSugar {

  "'make' function" should "allow initialization for objects" in {

    val createdAndInitializedValue = make(new ByteArrayOutputStream(128)) { baos =>
      baos.write(Array(1.toByte, 1.toByte, 0.toByte))
    }

    createdAndInitializedValue.toByteArray should be(Array(1.toByte, 1.toByte, 0.toByte))
  }

  "'using' function" should "call close after performing action on resource" in {

    val baos = mock[ByteArrayOutputStream]

    using(baos /* usually new ByteArrayOutputStream(128) */ ) { baos =>
      baos.write(Array(1.toByte, 1.toByte, 0.toByte))
    }

    verify(baos).close()
  }

  "Id" should "have equality and ordering working correctly" in {

    (Id[String]("1234213") === Id[String]("1234213")) should be(true)
    (Id[String]("1234213") === Id[String]("213414")) should be(false)
    (Id[String]("213414") === Id[String]("1234213")) should be(false)

    val ids    = Seq(Id[String]("4"), Id[String]("3"), Id[String]("2"), Id[String]("1"))
    val sorted = Seq(Id[String]("1"), Id[String]("2"), Id[String]("3"), Id[String]("4"))

    ids.sorted should contain theSameElementsInOrderAs sorted
  }

  it should "have type-safe conversions" in {
    final case class X(id: Id[X])
    final case class Y(id: Id[Y])
    final case class Z(id: Id[Z])

    implicit val xy = Id.Mapper[X, Y]
    implicit val yz = Id.Mapper[Y, Z]

    // Test that implicit conversions work correctly
    val x  = X(Id("0"))
    val y  = Y(x.id)
    val z  = Z(y.id)
    val y2 = Y(z.id)
    val x2 = X(y2.id)
    (x2 === x) should be(true)
    (y2 === y) should be(true)

    // Test that type inferrence for explicit conversions work correctly
    val yid = y.id
    val xid = xy(yid)
    val zid = yz(yid)
    (xid: Id[X]) should be(zid: Id[Z])
  }

  "UuidId" should "have equality and ordering working correctly" in {
    val uuidId1 = UuidId[String](UUID.fromString("ceec8358-cfa4-4e62-b601-1ba1f615f22f"))
    val uuidId2 = UuidId[String](UUID.fromString("1e761cbe-a5e0-4570-a503-818d14a2b322"))
    val uuidId3 = UuidId[String](UUID.fromString("326b2342-78b3-4ad4-8cbc-115e74019c39"))
    val uuidId4 = UuidId[String](UUID.fromString("46e38513-2117-45d9-a5df-1d899052fbb6"))

    (uuidId1 === UuidId[String](UUID.fromString("ceec8358-cfa4-4e62-b601-1ba1f615f22f"))) should be(true)
    (uuidId1 === uuidId2) should be(false)
    (uuidId2 === uuidId1) should be(false)

    val ids    = Seq(uuidId1, uuidId4, uuidId3, uuidId2)
    val sorted = Seq(uuidId1, uuidId2, uuidId3, uuidId4)

    ids.sorted should contain theSameElementsInOrderAs sorted
  }

  it should "have type-safe conversions" in {
    final case class X(id: UuidId[X])
    final case class Y(id: UuidId[Y])
    final case class Z(id: UuidId[Z])

    implicit val xy = UuidId.Mapper[X, Y]
    implicit val yz = UuidId.Mapper[Y, Z]

    // Test that implicit conversions work correctly
    val x  = X(UuidId(UUID.fromString("00000000-0000-0000-0000-00000000")))
    val y  = Y(x.id)
    val z  = Z(y.id)
    val y2 = Y(z.id)
    val x2 = X(y2.id)
    (x2 === x) should be(true)
    (y2 === y) should be(true)

    // Test that type inferrence for explicit conversions work correctly
    val yid = y.id
    val xid = xy(yid)
    val zid = yz(yid)
    (xid: UuidId[X]) should be(zid: UuidId[Z])
  }

  "NumericId" should "have equality and ordering working correctly" in {

    (NumericId[Long](1234213) === NumericId[Long](1234213)) should be(true)
    (NumericId[Long](1234213) === NumericId[Long](213414)) should be(false)
    (NumericId[Long](213414) === NumericId[Long](1234213)) should be(false)

    val ids    = Seq(NumericId[Long](4), NumericId[Long](3), NumericId[Long](2), NumericId[Long](1))
    val sorted = Seq(NumericId[Long](1), NumericId[Long](2), NumericId[Long](3), NumericId[Long](4))

    ids.sorted should contain theSameElementsInOrderAs sorted
  }

  it should "have type-safe conversions" in {
    final case class X(id: NumericId[X])
    final case class Y(id: NumericId[Y])
    final case class Z(id: NumericId[Z])

    implicit val xy = NumericId.Mapper[X, Y]
    implicit val yz = NumericId.Mapper[Y, Z]

    // Test that implicit conversions work correctly
    val x  = X(NumericId(0))
    val y  = Y(x.id)
    val z  = Z(y.id)
    val y2 = Y(z.id)
    val x2 = X(y2.id)
    (x2 === x) should be(true)
    (y2 === y) should be(true)

    // Test that type inferrence for explicit conversions work correctly
    val yid = y.id
    val xid = xy(yid)
    val zid = yz(yid)
    (xid: NumericId[X]) should be(zid: NumericId[Z])
  }

  "Name" should "have equality and ordering working correctly" in {

    (Name[String]("foo") === Name[String]("foo")) should be(true)
    (Name[String]("foo") === Name[String]("bar")) should be(false)
    (Name[String]("bar") === Name[String]("foo")) should be(false)

    val names  = Seq(Name[String]("d"), Name[String]("cc"), Name[String]("a"), Name[String]("bbb"))
    val sorted = Seq(Name[String]("a"), Name[String]("bbb"), Name[String]("cc"), Name[String]("d"))
    names.sorted should contain theSameElementsInOrderAs sorted
  }

  "Revision" should "have equality working correctly" in {

    val bla = Revision[String]("85569dab-a3dc-401b-9f95-d6fb4162674b")
    val foo = Revision[String]("f54b3558-bdcd-4646-a14b-8beb11f6b7c4")

    (bla === bla) should be(true)
    (bla === foo) should be(false)
    (foo === bla) should be(false)
  }

}
