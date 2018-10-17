package xyz.driver.core
package messaging

import java.util.concurrent.ConcurrentHashMap

import scala.async.Async.{async, await}
import scala.concurrent.Future

/** Utility mixin that will ensure that topics and subscriptions exist before
  * attempting to read or write from or to them.
  */
trait CreateOnDemand extends Bus {

  /** Create the given topic. This operation is idempotent and is expected to succeed if the topic
    * already exists.
    */
  def createTopic(topic: Topic[_]): Future[Unit]

  /** Create the given subscription. This operation is idempotent and is expected to succeed if the subscription
    * already exists.
    */
  def createSubscription(topic: Topic[_], config: SubscriptionConfig): Future[Unit]

  private val createdTopics        = new ConcurrentHashMap[Topic[_], Future[Unit]]
  private val createdSubscriptions = new ConcurrentHashMap[(Topic[_], SubscriptionConfig), Future[Unit]]

  private def ensureTopic(topic: Topic[_]) =
    createdTopics.computeIfAbsent(topic, t => createTopic(t))

  private def ensureSubscription(topic: Topic[_], config: SubscriptionConfig) =
    createdSubscriptions.computeIfAbsent(topic -> config, {
      case (t, c) => createSubscription(t, c)
    })

  abstract override def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit] = async {
    await(ensureTopic(topic))
    await(super.publishMessages(topic, messages))
  }

  abstract override def fetchMessages[A](
      topic: Topic[A],
      config: SubscriptionConfig,
      maxMessages: Int): Future[Seq[Message[A]]] = async {
    await(ensureTopic(topic))
    await(ensureSubscription(topic, config))
    await(super.fetchMessages(topic, config, maxMessages))
  }

}
