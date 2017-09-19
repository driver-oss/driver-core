package xyz.driver.core.trace

import com.google.cloud.trace.v1.consumer.TraceConsumer
import com.google.devtools.cloudtrace.v1.Traces
import com.typesafe.scalalogging.Logger

class LoggingTraceConsumer(log: Logger) extends TraceConsumer {
  import scala.collection.JavaConverters._
  def receive(trace: Traces): Unit = {
    for(t <- trace.getTracesList().asScala){
      log.info(s"received trace with id: ${t.getTraceId}")
    }
  }
}
