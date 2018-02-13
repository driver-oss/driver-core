package xyz.driver.core.rest

import java.util.UUID

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.server.PathMatchers.JavaUUID
import akka.http.scaladsl.server.{PathMatcher, PathMatcher1}
import eu.timepit.refined.collection.NonEmpty
import xyz.driver.core.time.Time
import xyz.driver.core.{Id, Name, NonEmptyName, Revision}

trait PathMatchers {

  private def UuidInPath[T]: PathMatcher1[Id[T]] =
    JavaUUID.map((id: UUID) => Id[T](id.toString.toLowerCase))

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

  def TimeInPath: PathMatcher1[Time] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(Time(string.toLong))
      catch { case _: IllegalArgumentException => None }
    }

  def RevisionInPath[T]: PathMatcher1[Revision[T]] =
    PathMatcher("""[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r) flatMap { string =>
      Some(Revision[T](string))
    }

  def NonEmptyNameInPath[T]: PathMatcher1[NonEmptyName[T]] = new PathMatcher1[NonEmptyName[T]] {
    import eu.timepit.refined.refineV
    def apply(path: Path) = path match {
      case Path.Segment(segment, tail) =>
        refineV[NonEmpty](segment) match {
          case Left(_)               => Unmatched
          case Right(nonEmptyString) => Matched(tail, Tuple1(NonEmptyName[T](nonEmptyString)))
        }
      case _ => Unmatched
    }
  }

}

object PathMatchers extends PathMatchers
