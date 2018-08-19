package xyz.driver.core.logging

import org.scalatest.FreeSpecLike
import xyz.driver.core.logging.phi._

class PhiStringContextTest extends FreeSpecLike {

  class Foo(x: Int, y: String) {
    val z: Boolean = true
  }

  case class Bar(y: Boolean)

  implicit def fooToPhiString(foo: Foo): NoPhiString = new NoPhiString(s"Foo(z=${foo.z})")

  "should not compile if there is no PhiString implicit" in assertDoesNotCompile(
    """val bar = Bar(true)
      |noPhi"bar is $bar"""".stripMargin
  )

  "should compile if there is a PhiString implicit" in assertCompiles(
    """val foo = new Foo(1, "test")
      |println(noPhi"foo is $foo}")""".stripMargin
  )

  "should not contain private info" in {
    val foo    = new Foo(42, "test")
    val result = noPhi"foo is $foo".text
    assert(!result.contains("test"))
    assert(!result.contains("42"))
  }
}
