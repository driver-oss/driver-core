package xyz.driver.core
package reporting

import scala.concurrent.Future

/** A reporter mixin that does not emit traces. */
trait NoTraceReporter extends Reporter {

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
