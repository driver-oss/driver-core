package xyz.driver

import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.collection.NonEmpty
import scalaz.{Equal, Monad, OptionT}
import xyz.driver.core.rest.errors.ExternalServiceException
import xyz.driver.core.tagging.Tagged

import scala.concurrent.{ExecutionContext, Future}

// TODO: this package seems too complex, look at all the features we need!
import scala.language.{higherKinds, implicitConversions, reflectiveCalls}

package object core {

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

  type @@[+V, +Tag] = V with Tagged[V, Tag]

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

  implicit class FutureExtensions[T](future: Future[T]) {
    def passThroughExternalServiceException(implicit executionContext: ExecutionContext): Future[T] =
      future.transform(identity, {
        case ExternalServiceException(_, _, Some(e)) => e
        case t: Throwable                            => t
      })
  }
}

package core {

  import java.util.UUID

  sealed trait GenericId[+Tag, IdType] extends Any {
    def value: IdType
    def length: Int
    def toString: String
  }

  final case class Id[+Tag](value: String) extends AnyVal with GenericId[Tag, String] {
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

  final case class UuidId[+Tag](value: UUID) extends AnyVal with GenericId[Tag, UUID] {
    @inline def length: Int       = value.toString.length
    override def toString: String = value.toString
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
  object UuidId {
    implicit def idEqual[T]: Equal[UuidId[T]]       = Equal.equal[UuidId[T]](_ == _)
    implicit def idOrdering[T]: Ordering[UuidId[T]] = Ordering.by[UuidId[T], UUID](_.value)

    sealed class Mapper[E, R] {
      def apply[T >: E](id: UuidId[R]): UuidId[T]                                = UuidId[E](id.value)
      def apply[T >: R](id: UuidId[E])(implicit dummy: DummyImplicit): UuidId[T] = UuidId[R](id.value)
    }
    object Mapper {
      def apply[E, R] = new Mapper[E, R]
    }
    implicit def convertRE[R, E](id: UuidId[R])(implicit mapper: Mapper[E, R]): UuidId[E] = mapper[E](id)
    implicit def convertER[E, R](id: UuidId[E])(implicit mapper: Mapper[E, R]): UuidId[R] = mapper[R](id)
  }

  final case class NumericId[+Tag](value: Long) extends AnyVal with GenericId[Tag, Long] {
    @inline def length: Int       = value.toString.length
    override def toString: String = value.toString
  }

  @SuppressWarnings(Array("org.wartremover.warts.ImplicitConversion"))
  object NumericId {
    implicit def idEqual[T]: Equal[NumericId[T]]       = Equal.equal[NumericId[T]](_ == _)
    implicit def idOrdering[T]: Ordering[NumericId[T]] = Ordering.by[NumericId[T], Long](_.value)

    sealed class Mapper[E, R] {
      def apply[T >: E](id: NumericId[R]): NumericId[T]                                = NumericId[E](id.value)
      def apply[T >: R](id: NumericId[E])(implicit dummy: DummyImplicit): NumericId[T] = NumericId[R](id.value)
    }
    object Mapper {
      def apply[E, R] = new Mapper[E, R]
    }
    implicit def convertRE[R, E](id: NumericId[R])(implicit mapper: Mapper[E, R]): NumericId[E] = mapper[E](id)
    implicit def convertER[E, R](id: NumericId[E])(implicit mapper: Mapper[E, R]): NumericId[R] = mapper[R](id)
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
