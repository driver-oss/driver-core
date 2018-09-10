package xyz.driver.core
package messaging

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.collection.mutable.ListBuffer

/** An extension to message buses that offers an Akka-Streams API.
  *
  * Example usage of a stream that subscribes to one topic, prints any messages
  * it receives and finally acknowledges them
  * their receipt.
  * {{{
  *   val bus: StreamBus = ???
  *   val topic = Topic.string("topic")
  *   bus.subscribe(topic1)
  *     .map{ msg =>
  *       print(msg.data)
  *       msg
  *     }
  *     .to(bus.acknowledge)
  *     .run()
  * }}} */
trait StreamBus extends Bus {

  /** Flow that publishes any messages to a given topic.
    * Emits messages once they have been published to the underlying bus. */
  def publish[A](topic: Topic[A]): Flow[A, A, NotUsed] = {
    Flow[A]
      .batch(defaultMaxMessages.toLong, a => ListBuffer[A](a))(_ += _)
      .mapAsync(1) { a =>
        publishMessages(topic, a).map(_ => a)
      }
      .mapConcat(list => list.toList)
  }

  /** Sink that acknowledges the receipt of a message. */
  def acknowledge: Sink[MessageId, NotUsed] = {
    Flow[MessageId]
      .batch(defaultMaxMessages.toLong, a => ListBuffer[MessageId](a))(_ += _)
      .mapAsync(1)(acknowledgeMessages(_))
      .to(Sink.ignore)
  }

  /** Source that listens to a subscription and receives any messages sent to its topic. */
  def subscribe[A](
      topic: Topic[A],
      config: SubscriptionConfig = defaultSubscriptionConfig): Source[Message[A], NotUsed] = {
    Source
      .unfoldAsync((topic, config))(
        topicAndConfig =>
          fetchMessages(topicAndConfig._1, topicAndConfig._2, defaultMaxMessages).map(msgs =>
            Some(topicAndConfig -> msgs))
      )
      .filter(_.nonEmpty)
      .mapConcat(messages => messages.toList)
  }

}
