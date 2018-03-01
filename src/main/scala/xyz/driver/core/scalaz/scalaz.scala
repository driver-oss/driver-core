package xyz.driver.core
package scalaz

import scalaz.syntax.Ops
import scalaz.Bind

// Extensions to things that flatMap
final class BindOpsEnglish[F[_], A](private val self: F[A])(implicit val F: Bind[F]) extends Ops[A] {
  private def flatMap[B](f: A => F[B]) = F.bind(self)(f)

  // FlatMap to b, discarding this value.
  // Equivalent to scalaz `self >> b`
  def andThen[B](b: => F[B]) = flatMap(_ => b)

  // FlatMap with f, but discard the resulting value.
  // Equivalent to scalaz: `self >>! f`
  def andFinally[B](f: A => F[B]) = flatMap(a => F.map(f(a))(_ => a))

  def zip[B](b: F[B]): F[(A, B)] = flatMap(a => F.map(b)((a, _)))

  def zipWith[B, C](b: F[B])(f: (A, B) => C): F[(A, B)] = flatMap(a => F.map(b)(f(a, _)))
}
