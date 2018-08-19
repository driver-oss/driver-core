package xyz.driver.core.logging

import java.net.{URI, URL}
import java.nio.file.Path
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import xyz.driver.core.time.Time

import scala.concurrent.duration.Duration

object phi {

  class NoPhiString(private[logging] val text: String) {
    // scalastyle:off
    @inline def +(that: NoPhiString) = new NoPhiString(this.text + that.text)
  }

  implicit class NoPhiStringContext(val sc: StringContext) extends AnyVal {
    def noPhi(args: NoPhiString*): NoPhiString = {
      val phiArgs = args.map(_.text)
      new NoPhiString(sc.s(phiArgs: _*))
    }
  }

  /**
    * Use it with care!
    */
  final case class NoPhi[T](private[logging] val value: T)
      extends NoPhiString(Option(value).map(_.toString).getOrElse("<null>"))

  // DO NOT ADD!
  // 1. phi"$fullName" is easier to write, than phi"${Unsafe(fullName)}"
  //   If you wrote the second version, it means that you know, what you doing.
  // 2. implicit def toPhiString(s: String): PhiString = Unsafe(s)

  implicit def booleanToPhiString(x: Boolean): NoPhiString = NoPhi(x.toString)

  implicit def uriToPhiString(x: URI): NoPhiString = NoPhi(x.toString)

  implicit def urlToPhiString(x: URL): NoPhiString = NoPhi(x.toString)

  implicit def pathToPhiString(x: Path): NoPhiString = NoPhi(x.toString)

  implicit def timeToPhiString(x: Time): NoPhiString = NoPhi(x.toString)

  implicit def localDateTimeToPhiString(x: LocalDateTime): NoPhiString = NoPhi(x.toString)

  implicit def localDateToPhiString(x: LocalDate): NoPhiString = NoPhi(x.toString)

  implicit def durationToPhiString(x: Duration): NoPhiString = NoPhi(x.toString)

  implicit def uuidToPhiString(x: UUID): NoPhiString = NoPhi(x.toString)

  implicit def tuple2ToPhiString[T1, T2](x: (T1, T2))(implicit inner1: T1 => NoPhiString,
                                                      inner2: T2 => NoPhiString): NoPhiString =
    x match { case (a, b) => noPhi"($a, $b)" }

  implicit def tuple3ToPhiString[T1, T2, T3](x: (T1, T2, T3))(implicit inner1: T1 => NoPhiString,
                                                              inner2: T2 => NoPhiString,
                                                              inner3: T3 => NoPhiString): NoPhiString =
    x match { case (a, b, c) => noPhi"($a, $b, $c)" }

  implicit def optionToPhiString[T](opt: Option[T])(implicit inner: T => NoPhiString): NoPhiString = opt match {
    case None    => noPhi"None"
    case Some(x) => noPhi"Some($x)"
  }

  implicit def iterableToPhiString[T](xs: Iterable[T])(implicit inner: T => NoPhiString): NoPhiString =
    NoPhi(xs.map(inner(_).text).mkString("Col(", ", ", ")"))
}
