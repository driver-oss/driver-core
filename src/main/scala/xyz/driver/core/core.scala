package xyz.driver

import scalaz.Equal

package object core {

  import scala.language.reflectiveCalls

  def make[T](v: => T)(f: T => Unit): T = {
    val value = v
    f(value)
    value
  }

  def using[R <: { def close() }, P](r: => R)(f: R => P): P = {
    val resource = r
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }

  object tagging {
    private[core] trait Tagged[+V, +Tag]
  }
  type @@[+V, +Tag] = V with tagging.Tagged[V, Tag]
}

package core {

  final case class Id[+Tag](value: String) extends AnyVal {
    @inline def length: Int       = value.length
    override def toString: String = value
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
  object Id {
    implicit def idEqual[T]: Equal[Id[T]]       = Equal.equal[Id[T]](_ == _)
    implicit def idOrdering[T]: Ordering[Id[T]] = Ordering.by[Id[T], String](_.value)

    sealed class Mapper[E, R] {
      def apply[T >: E](id: Id[R]): Id[T]                                = Id[E](id.value)
      def apply[T >: R](id: Id[E])(implicit dummy: DummyImplicit): Id[T] = Id[R](id.value)
    }
    object Mapper {
      def apply[E, R] = new Mapper[E, R]
    }
    implicit def convertRE[R, E](id: Id[R])(implicit mapper: Mapper[E, R]): Id[E] = mapper[E](id)
    implicit def convertER[E, R](id: Id[E])(implicit mapper: Mapper[E, R]): Id[R] = mapper[R](id)
  }

  final case class Name[+Tag](value: String) extends AnyVal {
    @inline def length: Int       = value.length
    override def toString: String = value
  }

  object Name {
    implicit def nameEqual[T]: Equal[Name[T]]       = Equal.equal[Name[T]](_ == _)
    implicit def nameOrdering[T]: Ordering[Name[T]] = Ordering.by(_.value)
  }

  object revision {
    final case class Revision[T](id: String)

    implicit def revisionEqual[T]: Equal[Revision[T]] = Equal.equal[Revision[T]](_.id == _.id)
  }
}
