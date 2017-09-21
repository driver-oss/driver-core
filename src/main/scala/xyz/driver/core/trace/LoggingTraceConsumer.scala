package xyz.driver.core.trace

import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.google.devtools.cloudtrace.v1.Traces
import com.typesafe.scalalogging.Logger

class LoggingTraceConsumer(log: Logger) extends TraceConsumer {
  def receive(traces: Traces): Unit = {
    log.trace(s"Received traces: $traces")
  }
}
