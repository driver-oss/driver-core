package xyz.driver.core
package messaging

import java.nio.ByteBuffer

import akka.actor.{Actor, ActorSystem, Props}

import scala.collection.mutable
import scala.collection.mutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

/** A bus backed by an asynchronous queue. Note that this bus requires a local actor system
  * and is intended for testing purposes only. */
class QueueBus(implicit system: ActorSystem) extends Bus {
  import system.dispatcher

  override type SubscriptionConfig = Long
  override val defaultSubscriptionConfig: Long = 0
  override val executionContext                = system.dispatcher

  override type MessageId = (String, SubscriptionConfig, Long)
  type Message[A]         = BasicMessage[A]

  private object ActorMessages {
    case class Send[A](topic: Topic[A], messages: Seq[A])
    case class Ack(messages: Seq[MessageId])
    case class Fetch[A](topic: Topic[A], cfg: SubscriptionConfig, max: Int, response: Promise[Seq[Message[A]]])
    case object Unack
  }

  class Subscription {
    val mailbox: mutable.Map[MessageId, ByteBuffer] = mutable.Map.empty
    val unacked: mutable.Map[MessageId, ByteBuffer] = mutable.Map.empty
  }

  private class BusMaster extends Actor {
    var _id = 0L
    def nextId(topic: String, cfg: SubscriptionConfig): (String, SubscriptionConfig, Long) = {
      _id += 1; (topic, cfg, _id)
    }

    val topics: mutable.Map[String, mutable.Map[SubscriptionConfig, Subscription]] = mutable.Map.empty

    def ensureSubscription(topic: String, cfg: SubscriptionConfig): Unit = {
      topics.get(topic) match {
        case Some(t) =>
          t.getOrElseUpdate(cfg, new Subscription)
        case None =>
          topics += topic -> mutable.Map.empty
          ensureSubscription(topic, cfg)
      }
    }

    override def preStart(): Unit = {
      context.system.scheduler.schedule(1.seconds, 1.seconds) {
        self ! ActorMessages.Unack
      }
    }

    override def receive: Receive = {
      case ActorMessages.Send(topic, messages) =>
        val buffers       = messages.map(topic.serialize)
        val subscriptions = topics.getOrElse(topic.name, Map.empty)
        for ((cfg, subscription) <- subscriptions) {
          for (buffer <- buffers) {
            subscription.mailbox += nextId(topic.name, cfg) -> buffer
          }
        }

      case ActorMessages.Fetch(topic, cfg, max, promise) =>
        ensureSubscription(topic.name, cfg)
        val subscription = topics(topic.name)(cfg)
        val messages     = subscription.mailbox.take(max)
        subscription.unacked ++= messages
        subscription.mailbox --= messages.map(_._1)
        promise.success(messages.toSeq.map {
          case (ackId, buffer) =>
            new Message[Any] {
              val id   = ackId
              val data = topic.deserialize(buffer)
            }
        })

      case ActorMessages.Ack(messageIds) =>
        for (id @ (topic, cfg, _) <- messageIds) {
          ensureSubscription(topic, cfg)
          val subscription = topics(topic)(cfg)
          subscription.unacked -= id
        }

      case ActorMessages.Unack =>
        for ((_, subscriptions) <- topics) {
          for ((_, subscription) <- subscriptions) {
            subscription.mailbox ++= subscription.unacked
            subscription.unacked.clear()
          }
        }
    }

  }

  val actor = system.actorOf(Props(new BusMaster))

  override def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit] = Future {
    actor ! ActorMessages.Send(topic, messages)
  }

  override def fetchMessages[A](
      topic: messaging.Topic[A],
      config: SubscriptionConfig,
      maxMessages: Int): Future[Seq[Message[A]]] = {
    val result = Promise[Seq[Message[A]]]
    actor ! ActorMessages.Fetch(topic, config, maxMessages, result)
    result.future
  }

  override def acknowledgeMessages(ids: Seq[MessageId]): Future[Unit] = Future {
    actor ! ActorMessages.Ack(ids)
  }

}

object QueueBus {
  def apply(implicit system: ActorSystem) = new QueueBus
}
