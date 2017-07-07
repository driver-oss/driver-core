package xyz.driver.core

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.pubsub.spi.v1.{AckReplyConsumer, MessageReceiver, Publisher, Subscriber}
import com.google.protobuf.ByteString
import com.google.pubsub.v1.{PubsubMessage, SubscriptionName, TopicName}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Try}

object pubsub {

  trait PubsubPublisher[Message] {

    type Result

    def publish(message: Message): Future[Result]
  }

  class GooglePubsubPublisher[Message](projectId: String, topicName: String, log: Logger)(
          implicit messageMarshaller: Marshaller[Message, String],
          ex: ExecutionContext
  ) extends PubsubPublisher[Message] {

    type Result = Id[PubsubMessage]

    private val publisher = Publisher.defaultBuilder(TopicName.create(projectId, topicName)).build()

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
              log.info(s"Published a message with topic $topicName, message id $messageId: $messageString")
              promise.complete(Try(Id[PubsubMessage](messageId)))
            }

            override def onFailure(t: Throwable): Unit = {
              log.warn(s"Failed to publish a message with topic $topicName: $message", t)
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

  class GooglePubsubSubscriber[Message](projectId: String,
                                        subscriptionId: String,
                                        receiver: Message => Future[Unit],
                                        log: Logger)(implicit messageMarshaller: Unmarshaller[String, Message],
                                                     mat: Materializer,
                                                     ex: ExecutionContext)
      extends PubsubSubscriber {

    private val subscriptionName = SubscriptionName.create(projectId, subscriptionId)

    private val messageReceiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val messageString = message.getData.toStringUtf8
        Unmarshal(messageString).to[Message].flatMap { messageBody =>
          log.info(s"Received a message ${message.getMessageId} for subscription $subscriptionId: $messageString")
          receiver(messageBody).transform(v => { consumer.ack(); v }, t => { consumer.nack(); t })
        }
      }
    }

    private val subscriber = Subscriber.defaultBuilder(subscriptionName, messageReceiver).build()

    subscriber.startAsync()

    override def stopListening(): Unit = {
      subscriber.stopAsync()
    }
  }

  class FakePubsubSubscriber extends PubsubSubscriber {
    def stopListening(): Unit = ()
  }
}
