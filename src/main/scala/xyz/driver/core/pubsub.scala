package xyz.driver.core

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.pubsub.v1._
import com.google.protobuf.ByteString
import com.google.pubsub.v1._
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Try}

object pubsub {

  trait PubsubPublisher[Message] {

    type Result

    def publish(message: Message): Future[Result]
  }

  class GooglePubsubPublisher[Message](projectId: String, topic: String, log: Logger, autoCreate: Boolean = true)(
      implicit messageMarshaller: Marshaller[Message, String],
      ex: ExecutionContext
  ) extends PubsubPublisher[Message] {

    type Result = Id[PubsubMessage]

    private val topicName = ProjectTopicName.of(projectId, topic)

    private val publisher = {
      if (autoCreate) {
        val adminClient = TopicAdminClient.create()
        val topicExists = Try(adminClient.getTopic(topicName)).isSuccess
        if (!topicExists) {
          adminClient.createTopic(topicName)
        }
      }
      Publisher.newBuilder(topicName).build()
    }

    override def publish(message: Message): Future[Id[PubsubMessage]] = {

      Marshal(message).to[String].flatMap { messageString =>
        val data          = ByteString.copyFromUtf8(messageString)
        val pubsubMessage = PubsubMessage.newBuilder().setData(data).build()

        val promise = Promise[Id[PubsubMessage]]()

        val messageIdFuture = publisher.publish(pubsubMessage)

        ApiFutures.addCallback(
          messageIdFuture,
          new ApiFutureCallback[String]() {
            override def onSuccess(messageId: String): Unit = {
              log.info(s"Published a message with topic $topic, message id $messageId: $messageString")
              promise.complete(Try(Id[PubsubMessage](messageId)))
            }

            override def onFailure(t: Throwable): Unit = {
              log.warn(s"Failed to publish a message with topic $topic: $message", t)
              promise.complete(Failure(t))
            }
          }
        )

        promise.future
      }
    }
  }

  class FakePubsubPublisher[Message](topicName: String, log: Logger)(
      implicit messageMarshaller: Marshaller[Message, String],
      ex: ExecutionContext)
      extends PubsubPublisher[Message] {

    type Result = Id[PubsubMessage]

    def publish(message: Message): Future[Result] =
      Marshal(message).to[String].map { messageString =>
        log.info(s"Published a message to a fake pubsub with topic $topicName: $messageString")
        generators.nextId[PubsubMessage]()
      }
  }

  trait PubsubSubscriber {

    def stopListening(): Unit
  }

  class GooglePubsubSubscriber[Message](
      projectId: String,
      subscriptionId: String,
      receiver: Message => Future[Unit],
      log: Logger,
      autoCreateSettings: Option[GooglePubsubSubscriber.SubscriptionSettings] = None
  )(implicit messageMarshaller: Unmarshaller[String, Message], mat: Materializer, ex: ExecutionContext)
      extends PubsubSubscriber {

    private val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)

    private val messageReceiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val messageString = message.getData.toStringUtf8
        Unmarshal(messageString).to[Message].flatMap { messageBody =>
          log.info(s"Received a message ${message.getMessageId} for subscription $subscriptionId: $messageString")
          receiver(messageBody).transform(v => { consumer.ack(); v }, t => { consumer.nack(); t })
        }
      }
    }

    private val subscriber = {
      autoCreateSettings.foreach { subscriptionSettings =>
        val adminClient        = SubscriptionAdminClient.create()
        val subscriptionExists = Try(adminClient.getSubscription(subscriptionName)).isSuccess
        if (!subscriptionExists) {
          val topicName = ProjectTopicName.of(projectId, subscriptionSettings.topic)
          adminClient.createSubscription(
            subscriptionName,
            topicName,
            subscriptionSettings.pushConfig,
            subscriptionSettings.ackDeadlineSeconds)
        }
      }

      Subscriber.newBuilder(subscriptionName, messageReceiver).build()
    }

    subscriber.startAsync()

    override def stopListening(): Unit = {
      subscriber.stopAsync()
    }
  }

  object GooglePubsubSubscriber {
    final case class SubscriptionSettings(topic: String, pushConfig: PushConfig, ackDeadlineSeconds: Int)
  }

  class FakePubsubSubscriber extends PubsubSubscriber {
    def stopListening(): Unit = ()
  }
}
