package xyz.driver.core
package init

import java.nio.file.Paths

import xyz.driver.core.discovery.CanDiscoverService
import xyz.driver.core.messaging.{GoogleBus, QueueBus, StreamBus}
import xyz.driver.core.reporting._
import xyz.driver.core.reporting.ScalaLoggerLike.defaultScalaLogger
import xyz.driver.core.storage.{BlobStorage, FileSystemBlobStorage, GcsBlobStorage}

import scala.concurrent.ExecutionContext

/** Mixin trait that provides essential cloud utilities. */
trait CloudServices extends AkkaBootable { self =>

  /** The platform that this application is running on.
    * @group config
    */
  def platform: Platform = Platform.current

  /** Service discovery for the current platform.
    *
    * Define a service trait and companion object:
    * {{{
    * trait MyService {
    *   def call(): Int
    * }
    * object MyService {
    *   implicit val isDiscoverable = new xyz.driver.core.discovery.CanDiscoverService[MyService] {
    *     def discover(p: xyz.driver.core.Platform): MyService = new MyService {
    *       def call() = 42
    *     }
    *   }
    * }
    * }}}
    *
    * Then discover and use it:
    * {{{
    * discover[MyService].call()
    * }}}
    *
    * @group utilities
    */
  def discover[A](implicit cds: CanDiscoverService[A]): A = cds.discover(platform)

  /* TODO: this reporter uses the platform to determine if JSON logging should be enabled.
   * Since the default logger uses slf4j, its settings must be specified before a logger
   * is first accessed. This in turn leads to somewhat convoluted code,
   * since we can't log when the platform being is determined.
   * A potential fix would be to make the log format independent of the platform, and always log
   * as JSON for example.
   */
  override lazy val reporter: Reporter with ScalaLoggerLike = {
    Console.println("determining platform") // scalastyle:ignore
    val r = platform match {
      case p @ Platform.GoogleCloud(_, _) =>
        new GoogleReporter(p.credentials, p.namespace, defaultScalaLogger(true))
      case Platform.Dev =>
        new NoTraceReporter(defaultScalaLogger(false))
    }
    r.info(s"application started on platform '${platform}'")(SpanContext.fresh())
    r
  }

  /** Object storage.
    * @group utilities
    */
  def storage(bucketId: String): BlobStorage =
    platform match {
      case Platform.GoogleCloud(keyfile, _) =>
        GcsBlobStorage.fromKeyfile(keyfile, bucketId)
      case Platform.Dev =>
        new FileSystemBlobStorage(Paths.get(s".data-$bucketId"))
    }

  /** Message bus.
    * @group utilities
    */
  def messageBus: StreamBus = platform match {
    case Platform.GoogleCloud(keyfile, namespace) => GoogleBus.fromKeyfile(keyfile, namespace)
    case Platform.Dev =>
      new QueueBus()(self.system) with StreamBus {
        override def executionContext: ExecutionContext = self.executionContext
      }
  }

}
