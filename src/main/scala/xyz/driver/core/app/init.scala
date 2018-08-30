package xyz.driver.core.app

import java.nio.file.{Files, Paths}
import java.time.Clock
import java.util.concurrent.{Executor, Executors}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import xyz.driver.core.logging.MdcExecutionContext
import xyz.driver.core.reporting.{NoTraceReporter, ScalaLoggerLike}
import xyz.driver.core.time.provider.TimeProvider
import xyz.driver.tracing.{GoogleTracer, NoTracer, Tracer}

import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.language.reflectiveCalls

object init {

  type RequiredBuildInfo = {
    val name: String
    val version: String
    val gitHeadCommit: scala.Option[String]
  }

  case class ApplicationContext(config: Config, clock: Clock, reporter: ScalaLoggerLike) {
    val time: TimeProvider = clock
  }

  /** NOTE: This needs to be the first that is run when application starts.
    * Otherwise if another command causes the logger to be instantiated,
    * it will default to logback.xml, and not honor this configuration
    */
  def configureLogging(): Unit = {
    scala.sys.env.get("JSON_LOGGING") match {
      case Some("true") =>
        System.setProperty("logback.configurationFile", "deployed-logback.xml")
      case _ =>
        System.setProperty("logback.configurationFile", "logback.xml")
    }
  }

  def getEnvironmentSpecificConfig(): Config = {
    scala.sys.env.get("APPLICATION_CONFIG_TYPE") match {
      case Some("deployed") =>
        ConfigFactory.load(this.getClass.getClassLoader, "deployed-application.conf")
      case _ =>
        xyz.driver.core.config.loadDefaultConfig
    }
  }

  def configureTracer(actorSystem: ActorSystem, applicationContext: ApplicationContext): Tracer = {

    val serviceAccountKeyFile =
      Paths.get(applicationContext.config.getString("tracing.google.serviceAccountKeyfile"))

    if (Files.exists(serviceAccountKeyFile)) {
      val materializer = ActorMaterializer()(actorSystem)
      new GoogleTracer(
        projectId = applicationContext.config.getString("tracing.google.projectId"),
        serviceAccountFile = serviceAccountKeyFile
      )(actorSystem, materializer)
    } else {
      applicationContext.reporter.logger.warn(s"Tracing file $serviceAccountKeyFile was not found, using NoTracer!")
      NoTracer
    }
  }

  def serviceActorSystem(serviceName: String, executionContext: ExecutionContext, config: Config): ActorSystem = {
    val actorSystem =
      ActorSystem(s"$serviceName-actors", Option(config), Option.empty[ClassLoader], Option(executionContext))

    sys.addShutdownHook {
      Try(actorSystem.terminate())
    }

    actorSystem
  }

  def toMdcExecutionContext(executor: Executor) =
    new MdcExecutionContext(ExecutionContext.fromExecutor(executor))

  def newFixedMdcExecutionContext(capacity: Int): MdcExecutionContext =
    toMdcExecutionContext(Executors.newFixedThreadPool(capacity))

  def defaultApplicationContext(): ApplicationContext =
    ApplicationContext(
      config = getEnvironmentSpecificConfig(),
      clock = Clock.systemUTC(),
      new NoTraceReporter(Logger(LoggerFactory.getLogger(classOf[DriverApp]))))

  def createDefaultApplication(
      modules: Seq[Module],
      buildInfo: RequiredBuildInfo,
      actorSystem: ActorSystem,
      tracer: Tracer,
      context: ApplicationContext): DriverApp = {
    val scheme  = context.config.getString("application.scheme")
    val baseUrl = context.config.getString("application.baseUrl")
    val port    = context.config.getInt("application.port")

    new DriverApp(
      buildInfo.name,
      buildInfo.version,
      buildInfo.gitHeadCommit.getOrElse("None"),
      modules = modules,
      context.time,
      context.reporter,
      context.config,
      interface = "0.0.0.0",
      baseUrl,
      scheme,
      port,
      tracer
    )(actorSystem, actorSystem.dispatcher)
  }

}
