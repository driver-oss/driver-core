package xyz.driver.core
package rest
package directives

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1, PathMatchers => AkkaPathMatchers}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import xyz.driver.core.time.Time

import scala.util.control.NonFatal

/** Akka-HTTP path matchers for custom core types. */
trait PathMatchers {

  private def UuidInPath[T]: PathMatcher1[Id[T]] =
    AkkaPathMatchers.JavaUUID.map((id: UUID) => Id[T](id.toString.toLowerCase))

  def IdInPath[T]: PathMatcher1[Id[T]] = UuidInPath[T] | new PathMatcher1[Id[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Id[T](segment)))
      case _                           => Unmatched
    }
  }

  def NameInPath[T]: PathMatcher1[Name[T]] = new PathMatcher1[Name[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) => Matched(tail, Tuple1(Name[T](segment)))
      case _                           => Unmatched
    }
  }

  private def timestampInPath: PathMatcher1[Long] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(string.toLong)
      catch { case _: IllegalArgumentException => None }
    }

  def InstantInPath: PathMatcher1[Instant] =
    new PathMatcher1[Instant] {
      def apply(path: Path): PathMatcher.Matching[Tuple1[Instant]] = path match {
        case Path.Segment(head, tail) =>
          try Matched(tail, Tuple1(Instant.parse(head)))
          catch {
            case NonFatal(_) => Unmatched
          }
        case _ => Unmatched
      }
    } | timestampInPath.map(Instant.ofEpochMilli)

  def TimeInPath: PathMatcher1[Time] = InstantInPath.map(instant => Time(instant.toEpochMilli))

  def NonEmptyNameInPath[T]: PathMatcher1[NonEmptyName[T]] = new PathMatcher1[NonEmptyName[T]] {
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) =>
        refineV[NonEmpty](segment) match {
          case Left(_)               => Unmatched
          case Right(nonEmptyString) => Matched(tail, Tuple1(NonEmptyName[T](nonEmptyString)))
        }
      case _ => Unmatched
    }
  }

  def RevisionInPath[T]: PathMatcher1[Revision[T]] =
    PathMatcher("""[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r) flatMap { string =>
      Some(Revision[T](string))
    }

}
