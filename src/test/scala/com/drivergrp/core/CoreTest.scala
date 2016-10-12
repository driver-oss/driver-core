package com.drivergrp.core

import java.io.ByteArrayOutputStream

import com.drivergrp.core.revision.Revision
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito._

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

    (Id[String](1234213L) === Id[String](1234213L)) should be(true)
    (Id[String](1234213L) === Id[String](213414L)) should be(false)
    (Id[String](213414L) === Id[String](1234213L)) should be(false)

    Seq(Id[String](4L), Id[String](3L), Id[String](2L), Id[String](1L)).sorted should contain
    theSameElementsInOrderAs(Seq(Id[String](1L), Id[String](2L), Id[String](3L), Id[String](4L)))
  }

  "Name" should "have equality and ordering working correctly" in {

    (Name[String]("foo") === Name[String]("foo")) should be(true)
    (Name[String]("foo") === Name[String]("bar")) should be(false)
    (Name[String]("bar") === Name[String]("foo")) should be(false)

    Seq(Name[String]("d"), Name[String]("cc"),        Name[String]("a"), Name[String]("bbb")).sorted should contain
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
