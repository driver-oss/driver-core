package xyz.driver.core

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

object future {

  implicit class RichFuture[T](f: Future[T]) {
    def mapAll[U](pf: PartialFunction[Try[T], U])(implicit executionContext: ExecutionContext): Future[U] = {
      val p = Promise[U]()
      f.onComplete(r => p.complete(Try(pf(r))))
      p.future
    }

    def failFastZip[U](that: Future[U])(implicit executionContext: ExecutionContext): Future[(T, U)] = {
      future.failFastZip(f, that)
    }
  }

  def failFastSequence[T](t: Iterable[Future[T]])(implicit ec: ExecutionContext): Future[Seq[T]] = {
    t.foldLeft(Future.successful(Nil: List[T])) { (f, i) =>
        failFastZip(f, i).map { case (tail, h) => h :: tail }
      }
      .map(_.reverse)
  }

  /**
    * Standard scala zip waits forever on the left side, even if the right side fails
    */
  def failFastZip[T, U](ft: Future[T], fu: Future[U])(implicit ec: ExecutionContext): Future[(T, U)] = {
    type State = Either[(T, Promise[U]), (U, Promise[T])]
    val middleState = Promise[State]()

    ft.onComplete {
      case f @ Failure(err) =>
        if (!middleState.tryFailure(err)) {
          // the right has already succeeded
          middleState.future.foreach {
            case Right((_, pt)) => pt.complete(f)
            case Left((t1, _)) => // This should never happen
              sys.error(s"Logic error: tried to set Failure($err) but Left($t1) already set")
          }
        }
      case Success(t) =>
        // Create the next promise:
        val pu = Promise[U]()
        if (!middleState.trySuccess(Left((t, pu)))) {
          // we can't set, so the other promise beat us here.
          middleState.future.foreach {
            case Right((_, pt)) => pt.success(t)
            case Left((t1, _)) => // This should never happen
              sys.error(s"Logic error: tried to set Left($t) but Left($t1) already set")
          }
        }
    }
    fu.onComplete {
      case f @ Failure(err) =>
        if (!middleState.tryFailure(err)) {
          // we can't set, so the other promise beat us here.
          middleState.future.foreach {
            case Left((_, pu)) => pu.complete(f)
            case Right((u1, _)) => // This should never happen
              sys.error(s"Logic error: tried to set Failure($err) but Right($u1) already set")
          }
        }
      case Success(u) =>
        // Create the next promise:
        val pt = Promise[T]()
        if (!middleState.trySuccess(Right((u, pt)))) {
          // we can't set, so the other promise beat us here.
          middleState.future.foreach {
            case Left((_, pu)) => pu.success(u)
            case Right((u1, _)) => // This should never happen
              sys.error(s"Logic error: tried to set Right($u) but Right($u1) already set")
          }
        }
    }

    middleState.future.flatMap {
      case Left((t, pu))  => pu.future.map((t, _))
      case Right((u, pt)) => pt.future.map((_, u))
    }
  }
}
