/** Code ported from "de.geekonaut" %% "slickmdc"  % "1.0.0"
  * License: @see https://github.com/AVGP/slickmdc/blob/master/LICENSE
  * Blog post: @see http://50linesofco.de/post/2016-07-01-slick-and-slf4j-mdc-logging-in-scala.html
  */
package xyz.driver.core.logging

import org.slf4j.MDC
import scala.concurrent.ExecutionContext

/**
  * Execution context proxy for propagating SLF4J diagnostic context from caller thread to execution thread.
  */
class MdcExecutionContext(executionContext: ExecutionContext) extends ExecutionContext {
  override def execute(runnable: Runnable): Unit = {
    val callerMdc = MDC.getCopyOfContextMap
    executionContext.execute(new Runnable {
      def run(): Unit = {
        // copy caller thread diagnostic context to execution thread
        Option(callerMdc).foreach(MDC.setContextMap)
        try {
          runnable.run()
        } finally {
          // the thread might be reused, so we clean up for the next use
          MDC.clear()
        }
      }
    })
  }

  override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)
}
