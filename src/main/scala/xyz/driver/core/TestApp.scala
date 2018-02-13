package xyz.driver.core

import spray.json._

object TestApp extends App with MacroProductFormats {

  @TypeFieldName("fooType")
  sealed trait Foo
  object Foo {
    final case class Bar(x: String) extends Foo
  }

  val foo: Foo = Foo.Bar("baz")

  println(s"Foo: $foo")
  println(s"Foo JSON: ${foo.toJson}")
  println(s"Foo parsed: ${foo.toJson.convertTo[Foo]}")
}
