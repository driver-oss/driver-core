package xyz.driver.core

import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.pubsub.spi.v1.{AckReplyConsumer, MessageReceiver, Publisher, Subscriber}
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{PubsubMessage, SubscriptionName, TopicName}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Try}

object pubsub {

  trait PubsubPublisher {

    type Message
    type Result

    def publish(message: Message): Future[Result]
  }

  class GooglePubsubPublisher(projectId: String, topicName: String, log: Logger) extends PubsubPublisher {

    type Message = String
    type Result  = Id[PubsubMessage]

    private val publisher = Publisher.defaultBuilder(TopicName.create(projectId, topicName)).build()

    override def publish(message: String): Future[Id[PubsubMessage]] = {

      val data          = ByteString.copyFromUtf8(message)
      val pubsubMessage = PubsubMessage.newBuilder().setData(data).build()

      val promise = Promise[Id[PubsubMessage]]()

      make(publisher.publish(pubsubMessage)) { messageIdFeature =>
        ApiFutures.addCallback(
          messageIdFeature,
          new ApiFutureCallback[String]() {
            override def onSuccess(messageId: String): Unit = {
              log.info(s"Published a message with topic $topicName, message id $messageId: $message")
              promise.complete(Try(Id[PubsubMessage](messageId)))
            }

            override def onFailure(t: Throwable): Unit = {
              log.warn(s"Failed to publish a message with topic $topicName: $message", t)
              promise.complete(Failure(t))
            }
          }
        )
      }

      promise.future
    }
  }

  trait PubsubSubscriber {

    def stopListening(): Unit
  }

  class GooglePubsubSubscriber[T](projectId: String, subscriptionId: String, receiver: String => Future[T], log: Logger)(
    implicit ex: ExecutionContext)
    extends PubsubSubscriber {

    private val subscriptionName = SubscriptionName.create(projectId, subscriptionId)

    private val messageReceiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val stringMessage = message.getData.toStringUtf8
        log.info(s"Received a message ${message.getMessageId} for subscription $subscriptionId: $stringMessage")
        receiver(stringMessage).transform(v => { consumer.ack(); v }, t => { consumer.nack(); t })
      }
    }

    private val subscriber = Subscriber.defaultBuilder(subscriptionName, messageReceiver).build()

    subscriber.startAsync()

    override def stopListening(): Unit = {
      subscriber.stopAsync()
    }
  }
}
