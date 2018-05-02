package xyz.driver.core.messaging

import akka.stream.scaladsl.Source

import scala.concurrent.Future
import scala.concurrent.duration._

trait MessageBus {

  trait Subscription {
    def cancel(): Unit
  }

  def handle[A](
      topic: Topic[A],
      uniqueSubscription: Option[String] = None,
      ackDelay: FiniteDuration = MessageBus.DefaultDelay)(action: PartialFunction[A, Future[Any]]): Future[Subscription]

  def send[A](topic: Topic[A], messages: Seq[A]): Future[Seq[A]]

  trait Message[A] {
    def ack(): Unit
    def nack(): Unit
    def data: A
  }
  object Message {
    def unapply[A](msg: Message[A]) = Some(msg.data)
  }

  def source[A](
      topic: Topic[A],
      uniqueSubscription: Option[String] = None,
      ackDelay: FiniteDuration = MessageBus.DefaultDelay): Source[Message[A], Subscription] = {
    ???
  }

}

object MessageBus {

  final val DefaultDelay: FiniteDuration = 90.seconds

}
