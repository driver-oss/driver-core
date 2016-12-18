package xyz.driver

import scalaz.Equal
import scala.annotation.implicitNotFound

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
}

package core {

  final case class Id[+Tag](value: String) extends AnyVal {
    @inline def length: Int       = value.length
    override def toString: String = value
  }

  object Id {
    implicit def idEqual[T]: Equal[Id[T]]       = Equal.equal[Id[T]](_ == _)
    implicit def idOrdering[T]: Ordering[Id[T]] = Ordering.by[Id[T], String](_.value)

    /**
      * Evidence that Id[A] can be safely converted to Id[B].
      * e.g. `implicit val CaseId = Id.SameId[Case, CasesRow]`
      * if `CaseId` is in scope, we can use either of:
      * `casesRowId.asId[Case]` or `caseId.asId[CasesRow]`
      *  Override convert for custom Id conversions.
      */
    @implicitNotFound("No evidence that ${A} has the same Id as ${B}")
    sealed trait SameId[A, B] {
      def convert(id: Id[A]): Id[B] = Id[B](id.value)
    }

    object SameId extends LowPrioritySameIdImplicits {
      def apply[A, B] = new SameId[A, B] {}

      implicit def reflexive[A]: A ~ A                        = SameId[A, A]
      implicit def symmetric[A, B](implicit ab: A ~ B): B ~ A = SameId[B, A]
    }

    trait LowPrioritySameIdImplicits {
      protected type ~[A, B] = SameId[A, B]

      implicit def transitive[A, B, C](implicit ab: A ~ B, bc: B ~ C): A ~ C = SameId[A, C]
    }

    implicit class InvariantIdOps[Tag](id: Id[Tag]) {
      def asId[T](implicit eq: SameId[Tag, T]): Id[T] = eq.convert(id)
    }
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
