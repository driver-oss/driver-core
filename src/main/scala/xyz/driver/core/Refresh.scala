package xyz.driver.core

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.Duration

/** A single-value asynchronous cache with TTL.
  *
  * Slightly adapted from
  * [[https://github.com/twitter/util/blob/ae0ab09134414438af9dfaa88a4613cecbff4741/util-cache/src/main/scala/com/twitter/cache/Refresh.scala
  * Twitter's "util" library]]
  *
  * Released under the Apache License 2.0.
  */
object Refresh {

  /** Creates a function that will provide a cached value for a given time-to-live (TTL).
    *
    * It avoids the "thundering herd" problem if multiple requests arrive
    * simultanously and the cached value has expired or is unset.
    *
    * Usage example:
    * {{{
    * def freshToken(): Future[String] = // expensive network call to get an access token
    * val getToken: () => Future[String] = Refresh.every(1.hour)(freshToken())
    *
    * getToken() // new token is issued
    * getToken() // subsequent calls use the cached token
    * // wait 1 hour
    * getToken() // new token is issued
    * }}}
    *
    * @param ttl Time-To-Live duration to cache a computed value.
    * @param compute Call-by-name operation that eventually computes a value to
    * be cached. Note that if the computation (i.e. the future) fails, the value
    * is not cached.
    * @param ec The execution context in which valeu computations will be run.
    * @return A zero-arg function that returns the cached value.
    */
  def every[A](ttl: Duration)(compute: => Future[A])(implicit ec: ExecutionContext): () => Future[A] = {
    val ref = new AtomicReference[(Future[A], Instant)](
      (Future.failed(new NoSuchElementException("Cached value was never computed")), Instant.MIN)
    )
    def refresh(): Future[A] = {
      val tuple                        = ref.get
      val (cachedValue, lastRetrieved) = tuple
      val now                          = Instant.now
      if (now.getEpochSecond < lastRetrieved.getEpochSecond + ttl.toSeconds) {
        cachedValue
      } else {
        val p         = Promise[A]
        val nextTuple = (p.future, now)
        if (ref.compareAndSet(tuple, nextTuple)) {
          compute.onComplete { done =>
            if (done.isFailure) {
              ref.set((p.future, lastRetrieved)) // don't update retrieval time in case of failure
            }
            p.complete(done)
          }
        }
        refresh()
      }
    }
    refresh _
  }

}
