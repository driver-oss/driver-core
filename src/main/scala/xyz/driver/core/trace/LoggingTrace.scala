package xyz.driver.core.trace

import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.typesafe.scalalogging.Logger

final class LoggingTrace(appName: String, appEnvironment: String, log: Logger)
    extends GoogleStackdriverTraceAbstractConsumer("logging-tracer", appName, appEnvironment) {

  override protected val traceConsumer: TraceConsumer = new LoggingTraceConsumer(log)
}
