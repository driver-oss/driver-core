package xyz.driver.core
package messaging

import scala.concurrent._
import scala.language.higherKinds

/** Base trait for representing message buses.
  *
  * Message buses are expected to provide "at least once" delivery semantics and are
  * expected to retry delivery when a message remains unacknowledged for a reasonable
  * amount of time. */
trait Bus {

  /** Type of unique message identifiers. Usually a string or UUID. */
  type MessageId

  /** Most general kind of message. Any implementation of a message bus must provide
    * the fields and methods specified in this trait. */
  trait BasicMessage[A] { self: Message[A] =>

    /** All messages must have unique IDs so that they can be acknowledged unambiguously. */
    def id: MessageId

    /** Actual message content. */
    def data: A

  }

  /** Actual type of messages provided by this bus. This must be a subtype of BasicMessage
    * (as that defines the minimal required fields of a messages), but may be refined to
    * provide bus-specific additional data. */
  type Message[A] <: BasicMessage[A]

  /** Type of a bus-specific configuration object can be used to tweak settings of subscriptions. */
  type SubscriptionConfig

  /** Default value for a subscription configuration. It is such that any service will have a unique subscription
    * for every topic, shared among all its instances. */
  val defaultSubscriptionConfig: SubscriptionConfig

  /** Maximum amount of messages handled in a single retrieval call. */
  val defaultMaxMessages = 64

  /** Execution context that is used to query and dispatch messages from this bus. */
  implicit val executionContext: ExecutionContext

  /** Retrieve any new messages in the mailbox of a subscription.
    *
    * Any retrieved messages become "outstanding" and should not be returned by this function
    * again until a reasonable (bus-specific) amount of time has passed and they remain unacknowledged.
    * In that case, they will again be considered new and will be returned by this function.
    *
    * Note that although outstanding and acknowledged messages will eventually be removed from
    * mailboxes, no guarantee can be made that a message will be delivered only once. */
  def fetchMessages[A](
      topic: Topic[A],
      config: SubscriptionConfig = defaultSubscriptionConfig,
      maxMessages: Int = defaultMaxMessages): Future[Seq[Message[A]]]

  /** Acknowledge that a given series of messages has been handled and should
    * not be delivered again.
    *
    * Note that messages become eventually acknowledged and hence may be delivered more than once.
    * @see fetchMessages()
    */
  def acknowledgeMessages(messages: Seq[MessageId]): Future[Unit]

  /** Send a series of messages to a topic.
    *
    * The returned future will complete once messages have been accepted to the underlying bus.
    * No guarantee can be made of their delivery to subscribers. */
  def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit]

}
