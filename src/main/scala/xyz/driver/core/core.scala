package xyz.driver

import scalaz.{Equal, Monad, OptionT}
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.collection.NonEmpty

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

  implicit class OptionTExtensions[H[_]: Monad, T](optionTValue: OptionT[H, T]) {

    def returnUnit: H[Unit] = optionTValue.fold[Unit](_ => (), ())

    def continueIgnoringNone: OptionT[H, Unit] =
      optionTValue.map(_ => ()).orElse(OptionT.some[H, Unit](()))

    def subflatMap[B](f: T => Option[B]): OptionT[H, B] =
      OptionT.optionT[H](implicitly[Monad[H]].map(optionTValue.run)(_.flatMap(f)))
  }

  implicit class MonadicExtensions[H[_]: Monad, T](monadicValue: H[T]) {
    private implicit val monadT = implicitly[Monad[H]]

    def returnUnit: H[Unit] = monadT(monadicValue)(_ => ())

    def toOptionT: OptionT[H, T] =
      OptionT.optionT[H](monadT(monadicValue)(value => Option(value)))

    def toUnitOptionT: OptionT[H, Unit] =
      OptionT.optionT[H](monadT(monadicValue)(_ => Option(())))
  }
}

package core {

  sealed trait Id[+Tag] {
    type Value
    val value: Value
    @inline def length: Int       = toString.length
    override def toString: String = value.toString
  }

  final case class StringId[+Tag](value: String)       extends Id[Tag] { type Value = String         }
  final case class LongId[+Tag](value: Long)           extends Id[Tag] { type Value = Long           }
  final case class UuidId[+Tag](value: java.util.UUID) extends Id[Tag] { type Value = java.util.UUID }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
  object Id {
    def apply[T](value: String): StringId[T] = StringId[T](value)

    implicit def idEqual[T, I[_] <: Id[_]]: Equal[I[T]] = Equal.equalA[I[T]]
    implicit def idOrdering[T, I[_] <: Id[_]](implicit ord: Ordering[I[T]#Value]): Ordering[I[T]] =
      Ordering.by[I[T], I[T]#Value](_.value)

    sealed class Mapper[E, R, I[_] <: Id[_]] {
      def apply[T >: E](id: I[R]): I[T]                                = id.asInstanceOf[I[T]]
      def apply[T >: R](id: I[E])(implicit dummy: DummyImplicit): I[T] = id.asInstanceOf[I[T]]
    }
    object Mapper {
      def apply[E, R, I[_] <: Id[_]] = new Mapper[E, R, I]
    }
    implicit def convertRE[R, E, I[_] <: Id[_]](id: I[R])(implicit mapper: Mapper[E, R, I]): I[E] = mapper[E](id)
    implicit def convertER[E, R, I[_] <: Id[_]](id: I[E])(implicit mapper: Mapper[E, R, I]): I[R] = mapper[R](id)
  }

  final case class Name[+Tag](value: String) extends AnyVal {
    @inline def length: Int       = value.length
    override def toString: String = value
  }

  object Name {
    implicit def nameEqual[T]: Equal[Name[T]]       = Equal.equal[Name[T]](_ == _)
    implicit def nameOrdering[T]: Ordering[Name[T]] = Ordering.by(_.value)

    implicit def nameValidator[T, P](implicit stringValidate: Validate[String, P]): Validate[Name[T], P] = {
      Validate.instance[Name[T], P, stringValidate.R](
        name => stringValidate.validate(name.value),
        name => stringValidate.showExpr(name.value))
    }
  }

  final case class NonEmptyName[+Tag](value: String Refined NonEmpty) {
    @inline def length: Int       = value.value.length
    override def toString: String = value.value
  }

  object NonEmptyName {
    implicit def nonEmptyNameEqual[T]: Equal[NonEmptyName[T]] =
      Equal.equal[NonEmptyName[T]](_.value.value == _.value.value)

    implicit def nonEmptyNameOrdering[T]: Ordering[NonEmptyName[T]] = Ordering.by(_.value.value)
  }

  final case class Revision[T](id: String)

  object Revision {
    implicit def revisionEqual[T]: Equal[Revision[T]] = Equal.equal[Revision[T]](_.id == _.id)
  }

  final case class Base64(value: String)
}
