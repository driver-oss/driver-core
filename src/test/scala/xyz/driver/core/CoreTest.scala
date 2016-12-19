package xyz.driver.core

import java.io.ByteArrayOutputStream

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.revision.Revision

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

    Seq(Id[String]("4"), Id[String]("3"), Id[String]("2"), Id[String]("1")).sorted should contain
    theSameElementsInOrderAs(Seq(Id[String]("1"), Id[String]("2"), Id[String]("3"), Id[String]("4")))
  }

  "Name" should "have equality and ordering working correctly" in {

    (Name[String]("foo") === Name[String]("foo")) should be(true)
    (Name[String]("foo") === Name[String]("bar")) should be(false)
    (Name[String]("bar") === Name[String]("foo")) should be(false)

    Seq(Name[String]("d"), Name[String]("cc"), Name[String]("a"), Name[String]("bbb")).sorted should contain
    theSameElementsInOrderAs(Seq(Name[String]("a"), Name[String]("bbb"), Name[String]("cc"), Name[String]("d")))
  }

  "Revision" should "have equality working correctly" in {

    val bla = Revision[String]("85569dab-a3dc-401b-9f95-d6fb4162674b")
    val foo = Revision[String]("f54b3558-bdcd-4646-a14b-8beb11f6b7c4")

    (bla === bla) should be(true)
    (bla === foo) should be(false)
    (foo === bla) should be(false)
  }
}
