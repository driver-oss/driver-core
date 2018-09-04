package xyz.driver.core
package init

import java.nio.file.Paths

import xyz.driver.core.messaging.{GoogleBus, QueueBus, StreamBus}
import xyz.driver.core.reporting._
import xyz.driver.core.reporting.ScalaLoggerLike.defaultScalaLogger
import xyz.driver.core.rest.{DnsDiscovery, ServiceDescriptor}
import xyz.driver.core.storage.{BlobStorage, FileSystemBlobStorage, GcsBlobStorage}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

/** Mixin trait that provides essential cloud utilities. */
trait CloudServices extends AkkaBootable { self =>

  /** The platform that this application is running on.
    * @group config
    */
  def platform: Platform = Platform.current

  /** Service discovery for the current platform.
    *
    */
  private lazy val discovery = {
    def getOverrides(): Map[String, String] = {
      val block = config.getObject("services.dev-overrides").unwrapped().asScala
      for ((key, value) <- block) yield {
        require(value.isInstanceOf[String], s"Service URL override for '$key' must be a string. Found '$value'.")
        key -> value.toString
      }
    }.toMap
    val overrides = platform match {
      case Platform.Dev => getOverrides()
      case _            => Map.empty[String, String] // TODO we may want to provide a way to override deployed services as well
    }
    new DnsDiscovery(clientTransport, overrides)
  }

  def discover[A: ServiceDescriptor]: A = discovery.discover[A]

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
    *
    * When running on a cloud platform, prepends `$project-` to bucket names, where `$project`
    * is the project ID (for example 'driverinc-production` or `driverinc-sandbox`).
    *
    * @group utilities
    */
  def storage(bucketName: String): BlobStorage =
    platform match {
      case p @ Platform.GoogleCloud(keyfile, _) =>
        GcsBlobStorage.fromKeyfile(keyfile, s"${p.project}-$bucketName")
      case Platform.Dev =>
        new FileSystemBlobStorage(Paths.get(s".data-$bucketName"))
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
