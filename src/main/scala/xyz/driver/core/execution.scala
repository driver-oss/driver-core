package xyz.driver.core

import scala.concurrent.{ExecutionContext, Future}
import scalaz.OptionT

object execution {

  implicit class FutureOptionTExtensions[T](future: Future[T])(implicit executionContext: ExecutionContext) {

    def toOptionT: OptionT[Future, T] =
      OptionT.optionT[Future](future.map(value => Option(value)))

    def returnUnit: Future[Unit] =
      future.map(_ => Option(()))

    def returnUnitOpt: OptionT[Future, Unit] =
      OptionT.optionT[Future](future.map(_ => Option(())))

    def andEffect[E](effect: Future[E]): Future[T] =
      for {
        result <- future
        _      <- effect
      } yield result

    def andEffect[E](effect: OptionT[Future, E]): Future[T] =
      andEffect(effect.run)
  }

  def illegalState[T](message: String): OptionT[Future, T] =
    failure[T](new IllegalStateException(message))

  def illegalArgument[T](message: String): OptionT[Future, T] =
    failure[T](new IllegalArgumentException(message))

  def failure[T](throwable: Throwable): OptionT[Future, T] =
    OptionT.optionT(Future.failed[Option[T]](throwable))

  def collectOrNone[T, R](value: T)(f: PartialFunction[T, OptionT[Future, R]]): OptionT[Future, R] =
    f.lift(value).getOrElse(OptionT.optionT(Future.successful(Option.empty[R])))

  def collectOrDoNothing[T](value: T)(f: PartialFunction[T, OptionT[Future, Unit]]): OptionT[Future, Unit] =
    f.lift(value).getOrElse(doNothing)

  val doNothing = OptionT.optionT(Future.successful(Option(())))
}
