package xyz.driver.core.trace

import com.google.cloud.trace.v1.consumer.{FlushableTraceConsumer}
import com.google.devtools.cloudtrace.v1.Traces
import com.typesafe.scalalogging.Logger
import scala.util.Try

/**
  * ExceptionLoggingFlushableTraceConsumer simply wraps a flushable trace consumer and catches/logs any exceptions
  * @param traceConsumer the flusable trace consumer to wrap
  * @param log where to log any exceptions
  */
class ExceptionLoggingFlushableTraceConsumer(traceConsumer: FlushableTraceConsumer, log: Logger)
    extends FlushableTraceConsumer {

  private val flushableTraceConsumer = traceConsumer

  private def exceptionLogger(exception: Throwable): Unit =
    log.error(s"Encountered exception logging to google $exception")

  override def receive(trace: Traces): Unit =
    Try(flushableTraceConsumer.receive(trace)).recover({case e => exceptionLogger(e)}).get

  override def flush(): Unit =
    Try(flushableTraceConsumer.flush()).recover({case e => exceptionLogger(e)}).get
}
