package xyz.driver.core
package reporting

import com.typesafe.scalalogging.Logger

import scala.concurrent.Future

class NoTraceReporter(val logger: Logger) extends Reporter with ScalaLoggerLike {
  override def traceWithOptionalParent[A](
      name: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, Reporter.CausalRelation)])(op: SpanContext => A): A = op(SpanContext.fresh())
  override def traceWithOptionalParentAsync[A](
      name: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, Reporter.CausalRelation)])(op: SpanContext => Future[A]): Future[A] =
    op(SpanContext.fresh())
}
