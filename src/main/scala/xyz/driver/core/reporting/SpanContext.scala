package xyz.driver.core
package reporting
import scala.util.Random

case class SpanContext private[core] (traceId: String, spanId: String)
object SpanContext {
  def fresh() = SpanContext(
    f"${Random.nextLong()}%02x${Random.nextLong()}%02x",
    f"${Random.nextLong()}%02x"
  )
}
