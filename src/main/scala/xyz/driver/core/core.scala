package xyz.driver

import scalaz.{Equal, Monad, OptionT}

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

  import java.nio.ByteBuffer
  import java.security.MessageDigest
  import java.util.UUID

  final case class Id[+Tag](value: String) extends AnyVal {
    @inline def length: Int       = value.length
    override def toString: String = value
    def humanReadableHashFromUuid(numChars: Int): String = {

      def byteToCharRange(intToMap: Int, lowerChar: Char, upperChar: Char) =
        (intToMap % (upperChar.toInt - lowerChar.toInt + 1) + lowerChar.toInt).toChar

      val uuid = UUID.fromString(value)

      val uuidBytes =
        ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits).putLong(uuid.getLeastSignificantBits).array()

      val fullHash = MessageDigest
        .getInstance("MD5")
        .digest(uuidBytes)
        .take(numChars)
        .map(_.toInt)
        .map(b => if (b < 0) 256 + b else b)

      val (firstBytes, secondBytes) = fullHash.splitAt(numChars / 2)

      val firstHalf  = secondBytes.map(byteToCharRange(_, 'A', 'Z')).mkString
      val secondHalf = firstBytes.map(byteToCharRange(_, '0', '9')).mkString

      s"$firstHalf - $secondHalf"
    }
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

  final case class Revision[T](id: String)

  object Revision {
    implicit def revisionEqual[T]: Equal[Revision[T]] = Equal.equal[Revision[T]](_.id == _.id)
  }

  final case class Base64(value: String)
}
