package xyz.driver.core.messaging

import xyz.driver.core.reporting.{Reporter, SpanContext}

import scala.concurrent.Future
import scala.language.higherKinds

trait ReportingBus extends Bus {

  def reporter: Reporter

  trait TracedMessage[A] extends BasicMessage[A] { self: Message[A] =>
    def spanContext: SpanContext
  }

  type Message[A] <: TracedMessage[A]

  abstract override def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit] = {
    super.publishMessages(topic, messages)
  }

  abstract override def fetchMessages[A](
      topic: Topic[A],
      config: SubscriptionConfig,
      maxMessages: Int): Future[Seq[Message[A]]] = {
    super.fetchMessages(topic, config, maxMessages)
  }

}

trait Topic2
trait Bus2 {
  def publishMessage[A](topic: Topic2, message: A)
}
