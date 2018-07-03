package xyz.driver.core

import java.util.concurrent.{Callable, TimeUnit}

import com.google.common.cache.{CacheBuilder, Cache => GuavaCache}
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{ExecutionContext, Future}

object cache {

  /**
    * FutureCache is used to represent an in-memory, in-process, asynchronous cache.
    *
    * Every cache operation is atomic.
    *
    * This implementation evicts failed results,
    * and doesn't interrupt the underlying request that has been fired off.
    */
  class AsyncCache[K, V](name: String, cache: GuavaCache[K, Future[V]])(implicit executionContext: ExecutionContext) {

    private[this] val log        = Logger(s"AsyncCache.$name")
    private[this] val underlying = cache.asMap()

    private[this] def evictOnFailure(key: K, f: Future[V]): Future[V] = {
      f.failed foreach {
        case ex: Throwable =>
          log.debug(s"Evict key $key due to exception $ex")
          evict(key, f)
      }
      f // we return the original future to make evict(k, f) easier to work with.
    }

    /**
      * Equivalent to getOrElseUpdate
      */
    def apply(key: K)(value: => Future[V]): Future[V] = getOrElseUpdate(key)(value)

    /**
      * Gets the cached Future.
      *
      * @return None if a value hasn't been specified for that key yet
      *         Some(ksync computation) if the value has been specified.  Just
      *         because this returns Some(..) doesn't mean that it has been
      *         satisfied, but if it hasn't been satisfied, it's probably
      *         in-flight.
      */
    def get(key: K): Option[Future[V]] = Option(underlying.get(key))

    /**
      * Gets the cached Future, or if it hasn't been returned yet, computes it and
      * returns that value.
      */
    def getOrElseUpdate(key: K)(compute: => Future[V]): Future[V] = {
      log.debug(s"Try to retrieve key $key from cache")
      evictOnFailure(key, cache.get(key, new Callable[Future[V]] {
        def call(): Future[V] = {
          log.debug(s"Cache miss, load the key: $key")
          compute
        }
      }))
    }

    /**
      * Unconditionally sets a value for a given key
      */
    def set(key: K, value: Future[V]): Unit = {
      cache.put(key, value)
      evictOnFailure(key, value)
    }

    /**
      * Evicts the contents of a `key` if the old value is `value`.
      *
      * Since `scala.concurrent.Future` uses reference equality, you must use the
      * same object reference to evict a value.
      *
      * @return true if the key was evicted
      *         false if the key was not evicted
      */
    def evict(key: K, value: Future[V]): Boolean = underlying.remove(key, value)

    /**
      * @return the number of results that have been computed successfully or are in flight.
      */
    def size: Int = cache.size.toInt
  }

  object AsyncCache {
    val DEFAULT_CAPACITY: Long             = 10000L
    val DEFAULT_READ_EXPIRATION: Duration  = 10 minutes
    val DEFAULT_WRITE_EXPIRATION: Duration = 1 hour

    def apply[K <: AnyRef, V <: AnyRef](
        name: String,
        capacity: Long = DEFAULT_CAPACITY,
        readExpiration: Duration = DEFAULT_READ_EXPIRATION,
        writeExpiration: Duration = DEFAULT_WRITE_EXPIRATION)(
        implicit executionContext: ExecutionContext): AsyncCache[K, V] = {
      val guavaCache = CacheBuilder
        .newBuilder()
        .maximumSize(capacity)
        .expireAfterAccess(readExpiration.toSeconds, TimeUnit.SECONDS)
        .expireAfterWrite(writeExpiration.toSeconds, TimeUnit.SECONDS)
        .build[K, Future[V]]()
      new AsyncCache(name, guavaCache)
    }
  }
}
