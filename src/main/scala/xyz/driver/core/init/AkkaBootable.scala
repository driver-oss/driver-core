package xyz.driver.core
package init

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.ByteString
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import com.typesafe.config.Config
import kamon.Kamon
import kamon.statsd.StatsDReporter
import kamon.system.SystemMetrics
import xyz.driver.core.reporting.{NoTraceReporter, Reporter, ScalaLoggerLike, SpanContext}
import xyz.driver.core.rest.HttpRestServiceTransport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/** Provides standard scaffolding for applications that use Akka HTTP.
  *
  * Among the features provided are:
  *
  *   - execution contexts of various kinds
  *   - basic JVM metrics collection via Kamon
  *   - startup and shutdown hooks
  *
  * This trait provides a minimal, runnable application. It is designed to be extended by various mixins (see
  * Known Subclasses) in this package.
  *
  * By implementing a "main" method, mixing this trait into a singleton object will result in a runnable
  * application.
  * I.e.
  * {{{
  *   object Main extends AkkaBootable // this is a runnable application
  * }}}
  * In case this trait isn't mixed into a top-level singleton object, the [[AkkaBootable#main main]] method should
  * be called explicitly, in order to initialize and start this application.
  * I.e.
  * {{{
  *   object Main {
  *     val bootable = new AkkaBootable {}
  *     def main(args: Array[String]): Unit = {
  *       bootable.main(args)
  *     }
  *   }
  * }}}
  *
  * @groupname config Configuration
  * @groupname contexts Contexts
  * @groupname utilities Utilities
  * @groupname hooks Overrideable Hooks
  */
trait AkkaBootable {

  /** The application's name. This value is extracted from the build configuration.
    * @group config
    */
  def name: String = BuildInfoReflection.name

  /** The application's version (or git sha). This value is extracted from the build configuration.
    * @group config
    */
  def version: Option[String] = BuildInfoReflection.version

  /** TCP port that this application will listen on.
    * @group config
    */
  def port: Int = 8080

  // contexts
  /** General-purpose actor system for this application.
    * @group contexts
    */
  implicit lazy val system: ActorSystem = ActorSystem("app")

  /** General-purpose stream materializer for this application.
    * @group contexts
    */
  implicit lazy val materializer: Materializer = ActorMaterializer()

  /** General-purpose execution context for this application.
    *
    * Note that no thread-blocking tasks should be submitted to this context. In cases that do require blocking,
    * a custom execution context should be defined and used. See
    * [[https://doc.akka.io/docs/akka-http/current/handling-blocking-operations-in-akka-http-routes.html this guide]]
    * on how to configure custom execution contexts in Akka.
    *
    * @group contexts
    */
  implicit lazy val executionContext: ExecutionContext = system.dispatcher

  /** Default HTTP client, backed by this application's actor system.
    * @group contexts
    */
  implicit lazy val httpClient: SttpBackend[Future, Source[ByteString, Any]] = AkkaHttpBackend.usingActorSystem(system)

  /** Old HTTP client system. Prefer using an sttp backend for new service clients.
    * @group contexts
    * @see httpClient
    */
  implicit lazy val clientTransport: HttpRestServiceTransport = new HttpRestServiceTransport(
    applicationName = Name(name),
    applicationVersion = version.getOrElse("<unknown>"),
    actorSystem = system,
    executionContext = executionContext,
    log = reporter
  )

  // utilities
  /** Default reporter instance.
    *
    * Note that this is currently defined to be a ScalaLoggerLike, so that it can be implicitly converted to a
    * [[com.typesafe.scalalogging.Logger]] when necessary. This conversion is provided to ensure backwards
    * compatibility with code that requires such a logger. Warning: using a logger instead of a reporter will
    * not include tracing information in any messages!
    *
    * @group utilities
    */
  def reporter: Reporter with ScalaLoggerLike = new NoTraceReporter(ScalaLoggerLike.defaultScalaLogger(json = false))

  /** Top-level application configuration.
    *
    * TODO: should we expose some config wrapper rather than the typesafe config library?
    * (Author's note: I'm a fan of TOML since it's so simple. There's already an implementation for Scala
    * [[https://github.com/jvican/stoml]].)
    *
    * @group utilities
    */
  def config: Config = system.settings.config

  /** Overridable startup hook.
    *
    * Invoked by [[main]] during application startup.
    *
    * @group hooks
    */
  def startup(): Unit = ()

  /** Overridable shutdown hook.
    *
    * Invoked on an arbitrary thread when a shutdown signal is caught.
    *
    * @group hooks
    */
  def shutdown(): Unit = ()

  /** Overridable HTTP route.
    *
    * Any services that present an HTTP interface should implement this method.
    *
    * @group hooks
    * @see [[HttpApi]]
    */
  def route: Route = (ctx: RequestContext) => ctx.complete(StatusCodes.NotFound)

  private def syslog(message: String)(implicit ctx: SpanContext) = reporter.info(s"application: " + message)

  /** This application's entry point. */
  def main(args: Array[String]): Unit = {
    implicit val ctx = SpanContext.fresh()
    syslog("initializing metrics collection")
    Kamon.addReporter(new StatsDReporter())
    SystemMetrics.startCollecting()

    system.registerOnTermination {
      syslog("running shutdown hooks")
      shutdown()
      syslog("bye!")
    }

    syslog("running startup hooks")
    startup()

    syslog("binding to network interface")
    val binding = Await.result(
      Http().bindAndHandle(route, "::", port),
      2.seconds
    )
    syslog(s"listening to ${binding.localAddress}")

    syslog("startup complete")
  }

}
