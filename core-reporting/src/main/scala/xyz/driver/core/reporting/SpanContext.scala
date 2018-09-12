package xyz.driver.core
package reporting

import scala.util.Random

case class SpanContext private[core] (traceId: String, spanId: String)

object SpanContext {
  def fresh(): SpanContext = SpanContext(
    f"${Random.nextLong()}%016x${Random.nextLong()}%016x",
    f"${Random.nextLong()}%016x"
  )
}
