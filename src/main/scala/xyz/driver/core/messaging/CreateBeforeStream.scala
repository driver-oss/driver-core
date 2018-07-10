package xyz.driver.core
package messaging

import akka.stream.scaladsl.{Flow, Source}

import scala.async.Async.{async, await}
import scala.concurrent.Future

/** Utility mixin that will ensure that topics and subscriptions exist before attempting to stream from or to them. */
trait CreateBeforeStream extends StreamBus {

  def createTopic(topic: Topic[_]): Future[Unit]
  def createSubscription(topic: Topic[_], config: SubscriptionConfig): Future[Unit]

  override def publish[A](topic: Topic[A]): Flow[A, A, _] = {
    def create(): Future[Flow[A, A, _]] = async {
      await(createTopic(topic))
      super.publish(topic)
    }
    Flow.lazyInitAsync[A, A, Any](() => create())
  }

  override def subscribe[A](topic: Topic[A], config: SubscriptionConfig): Source[Message[A], _] = {
    def create(): Future[Source[Message[A], _]] = async {
      await(createTopic(topic))
      await(createSubscription(topic, config))
      super.subscribe(topic, config)
    }
    Source.fromFutureSource[Message[A], Any](create())
  }

}
