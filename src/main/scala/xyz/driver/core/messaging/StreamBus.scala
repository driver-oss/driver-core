package xyz.driver.core
package messaging

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, RestartSource, Sink, Source}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._

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

  def runWithRestart[A](
      topic: Topic[A],
      config: SubscriptionConfig = defaultSubscriptionConfig,
      minBackoff: FiniteDuration = 3.seconds,
      maxBackoff: FiniteDuration = 30.seconds,
      randomFactor: Double = 0.2,
      maxRestarts: Int = 20
  )(processMessage: Flow[Message[A], List[MessageId], NotUsed])(implicit mat: Materializer): NotUsed = {
    RestartSource
      .withBackoff[MessageId](
        minBackoff,
        maxBackoff,
        randomFactor,
        maxRestarts
      ) { () =>
        subscribe(topic, config)
          .via(processMessage.recover({ case _ => Nil }))
          .log(topic.name)
          .mapConcat(identity)
      }
      .to(acknowledge)
      .run()
  }

  def handleMessage[A](
      topic: Topic[A],
      config: SubscriptionConfig = defaultSubscriptionConfig,
      parallelism: Int = 1,
      minBackoff: FiniteDuration = 3.seconds,
      maxBackoff: FiniteDuration = 30.seconds,
      randomFactor: Double = 0.2,
      maxRestarts: Int = 20
  )(processMessage: A => Future[_])(implicit mat: Materializer): NotUsed = {
    runWithRestart(topic, config, minBackoff, maxBackoff, randomFactor, maxRestarts) {
      Flow[Message[A]].mapAsync(parallelism) { message =>
        processMessage(message.data).map(_ => message.id :: Nil)
      }
    }
  }
}
