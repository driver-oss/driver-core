package xyz.driver.core
package reporting

import scala.concurrent.Future

/** Context-aware diagnostic utility for distributed systems, combining logging and tracing.
  *
  * Diagnostic messages (i.e. logs) are a vital tool for monitoring applications. Tying such messages to an
  * execution context, such as a stack trace, simplifies debugging greatly by giving insight to the chains of events
  * that led to a particular message. In synchronous systems, execution contexts can easily be determined by an
  * external observer, and, as such, do not need to be propagated explicitly to sub-components (e.g. a stack trace on
  * the JVM shows all relevant information). In asynchronous systems and especially distributed systems however,
  * execution contexts are not easily determined by an external observer and hence need to be explicitly passed across
  * service boundaries.
  *
  * This reporter provides tracing and logging utilities that explicitly require references to execution contexts
  * (called [[SpanContext]]s here) intended to be passed across service boundaries. It embraces Scala's
  * implicit-parameter-as-a-context paradigm.
  *
  * Tracing is intended to be compatible with the
  * [[https://github.com/opentracing/specification/blob/master/specification.md OpenTrace specification]], and hence its
  * guidelines on naming and tagging apply to methods provided by this Reporter as well.
  *
  * Usage example:
  * {{{
  * val reporter: Reporter = ???
  * object Repo {
  *   def getUserQuery(userId: String)(implicit ctx: SpanContext) = reporter.trace("query"){ implicit ctx =>
  *     reporter.debug("Running query")
  *     // run query
  *   }
  * }
  * object Service {
  *   def getUser(userId: String)(implicit ctx: SpanContext) = reporter.trace("get_user"){ implicit ctx =>
  *     reporter.debug("Getting user")
  *     Repo.getUserQuery(userId)
  *   }
  * }
  * reporter.traceRoot("static_get", Map("user" -> "john")) { implicit ctx =>
  *   Service.getUser("john")
  * }
  * }}}
  *
  * '''Note that computing traces may be a more expensive operation than traditional logging frameworks provide (in terms
  * of memory and processing). It should be used in interesting and actionable code paths.'''
  *
  * @define rootWarning Note: the idea of the reporting framework is to pass along references to traces as
  *                     implicit parameters. This method should only be used for top-level traces when no parent
  *                     traces are available.
  */
trait Reporter {
  import Reporter._

  def traceWithOptionalParent[A](
      name: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, CausalRelation)])(op: SpanContext => A): A
  def traceWithOptionalParentAsync[A](
      name: String,
      tags: Map[String, String],
      parent: Option[(SpanContext, CausalRelation)])(op: SpanContext => Future[A]): Future[A]

  /** Trace the execution of an operation, if no parent trace is available.
    *
    * $rootWarning
    */
  final def traceRoot[A](name: String, tags: Map[String, String] = Map.empty)(op: SpanContext => A): A =
    traceWithOptionalParent(
      name,
      tags,
      None
    )(op)

  /** Trace the execution of an asynchronous operation, if no parent trace is available.
    *
    * $rootWarning
    *
    * @see traceRoot
    */
  final def traceRootAsync[A](name: String, tags: Map[String, String] = Map.empty)(
      op: SpanContext => Future[A]): Future[A] =
    traceWithOptionalParentAsync(
      name,
      tags,
      None
    )(op)

  /** Trace the execution of an operation, in relation to a parent context.
    *
    * @param name The name of the operation. Note that this name should not be too specific. According to the
    *             OpenTrace RFC: "An operation name, a human-readable string which concisely represents the work done
    *             by the Span (for example, an RPC method name, a function name, or the name of a subtask or stage
    *             within a larger computation). The operation name should be the most general string that identifies a
    *             (statistically) interesting class of Span instances. That is, `"get_user"` is better than
    *             `"get_user/314159"`".
    * @param tags Attributes associated with the traced event. Following the above example, if `"get_user"` is an
    *             operation name, a good tag would be `("account_id" -> 314159)`.
    * @param relation Relation of the operation to its parent context.
    * @param op The operation to be traced. The trace will complete once the operation returns.
    * @param ctx Context of the parent trace.
    * @tparam A Return type of the operation.
    * @return The value of the child operation.
    */
  final def trace[A](
      name: String,
      tags: Map[String, String] = Map.empty,
      relation: CausalRelation = CausalRelation.Child)(op: /* implicit (gotta wait for Scala 3) */ SpanContext => A)(
      implicit ctx: SpanContext): A =
    traceWithOptionalParent(
      name,
      tags,
      Some(ctx -> relation)
    )(op)

  /** Trace the operation of an asynchronous operation.
    *
    * Contrary to the synchronous version of this method, a trace is completed once the child operation completes
    * (rather than returns).
    *
    * @see trace
    */
  final def traceAsync[A](
      name: String,
      tags: Map[String, String] = Map.empty,
      relation: CausalRelation = CausalRelation.Child)(
      op: /* implicit (gotta wait for Scala 3) */ SpanContext => Future[A])(implicit ctx: SpanContext): Future[A] =
    traceWithOptionalParentAsync(
      name,
      tags,
      Some(ctx -> relation)
    )(op)

  /** Log a message. */
  def log(severity: Severity, message: String, reason: Option[Throwable])(implicit ctx: SpanContext): Unit

  /** Log a debug message. */
  final def debug(message: String)(implicit ctx: SpanContext): Unit = log(Severity.Debug, message, None)
  final def debug(message: String, reason: Throwable)(implicit ctx: SpanContext): Unit =
    log(Severity.Debug, message, Some(reason))

  /** Log an informational message. */
  final def info(message: String)(implicit ctx: SpanContext): Unit = log(Severity.Informational, message, None)
  final def info(message: String, reason: Throwable)(implicit ctx: SpanContext): Unit =
    log(Severity.Informational, message, Some(reason))

  /** Log a warning message. */
  final def warn(message: String)(implicit ctx: SpanContext): Unit = log(Severity.Warning, message, None)
  final def warn(message: String, reason: Throwable)(implicit ctx: SpanContext): Unit =
    log(Severity.Warning, message, Some(reason))

  /** Log an error message. */
  final def error(message: String)(implicit ctx: SpanContext): Unit = log(Severity.Error, message, None)
  final def error(message: String, reason: Throwable)(implicit ctx: SpanContext): Unit =
    log(Severity.Error, message, Some(reason))

}

object Reporter {

  /** A relation in cause.
    *
    * Corresponds to
    * [[https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans OpenTrace references between spans]]
    */
  sealed trait CausalRelation
  object CausalRelation {

    /** One event is the child of another. The parent completes once the child is complete. */
    case object Child extends CausalRelation

    /** One event follows from another, not necessarily being the parent. */
    case object Follows extends CausalRelation
  }

  sealed trait Severity
  object Severity {
    case object Debug         extends Severity
    case object Informational extends Severity
    case object Warning       extends Severity
    case object Error         extends Severity
  }

}
