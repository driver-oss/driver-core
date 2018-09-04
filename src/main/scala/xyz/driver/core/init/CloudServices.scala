package xyz.driver.core
package init

import java.nio.file.Paths

import com.typesafe.config.ConfigValueType
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
    def getOverrides(): Map[String, String] =
      (for {
        obj   <- config.getObjectList("services.dev-overrides").asScala
        entry <- obj.entrySet().asScala
      } yield {
        val tpe = entry.getValue.valueType()
        require(
          tpe == ConfigValueType.STRING,
          s"URL override for '${entry.getKey}' must be a " +
            s"string. Found '${entry.getValue.unwrapped}', which is of type $tpe.")
        entry.getKey -> entry.getValue.unwrapped.toString
      }).toMap

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
