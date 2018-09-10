package xyz.driver.core.messaging
import java.nio.ByteBuffer
import java.util

import com.aliyun.mns.client.{AsyncCallback, CloudAccount}
import com.aliyun.mns.common.ServiceException
import com.aliyun.mns.model
import com.aliyun.mns.model._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

class AliyunBus(
    accountId: String,
    accessId: String,
    accessSecret: String,
    region: String,
    namespace: String,
    pullTimeout: Int)(implicit val executionContext: ExecutionContext)
    extends Bus {
  val endpoint     = s"https://$accountId.mns.$region.aliyuncs.com"
  val cloudAccount = new CloudAccount(accessId, accessSecret, endpoint)
  val client       = cloudAccount.getMNSClient

  override val defaultMaxMessages: Int = 10

  case class MessageId(queueName: String, messageHandle: String)

  case class Message[A](id: MessageId, data: A) extends BasicMessage[A]

  case class SubscriptionConfig(
      subscriptionPrefix: String = accessId,
      ackTimeout: FiniteDuration = 10.seconds
  )

  override val defaultSubscriptionConfig: SubscriptionConfig = SubscriptionConfig()

  private def rawTopicName(topic: Topic[_]) =
    s"$namespace-${topic.name}"
  private def rawSubscriptionName(config: SubscriptionConfig, topic: Topic[_]) =
    s"$namespace-${config.subscriptionPrefix}-${topic.name}"

  override def fetchMessages[A](
      topic: Topic[A],
      config: SubscriptionConfig,
      maxMessages: Int): Future[Seq[Message[A]]] = {
    import collection.JavaConverters._
    val subscriptionName = rawSubscriptionName(config, topic)
    val queueRef         = client.getQueueRef(subscriptionName)

    val promise = Promise[Seq[model.Message]]
    queueRef.asyncBatchPopMessage(
      maxMessages,
      pullTimeout,
      new AsyncCallback[util.List[model.Message]] {
        override def onSuccess(result: util.List[model.Message]): Unit = promise.success(result.asScala)
        override def onFail(ex: Exception): Unit = ex match {
          case serviceException: ServiceException if serviceException.getErrorCode == "MessageNotExist" =>
            promise.success(Nil)
          case _ => promise.failure(ex)
        }
      }
    )

    promise.future.map(_.map { message =>
      import scala.xml.XML
      val messageId    = MessageId(subscriptionName, message.getReceiptHandle)
      val messageXML   = XML.loadString(message.getMessageBodyAsRawString)
      val messageNode  = messageXML \ "Message"
      val messageBytes = java.util.Base64.getDecoder.decode(messageNode.head.text)

      val deserializedMessage = topic.deserialize(ByteBuffer.wrap(messageBytes))
      Message(messageId, deserializedMessage)
    })
  }

  override def acknowledgeMessages(messages: Seq[MessageId]): Future[Unit] = {
    import collection.JavaConverters._
    require(messages.nonEmpty, "Acknowledged message list must be non-empty")

    val queueRef = client.getQueueRef(messages.head.queueName)

    val promise = Promise[Unit]
    queueRef.asyncBatchDeleteMessage(
      messages.map(_.messageHandle).asJava,
      new AsyncCallback[Void] {
        override def onSuccess(result: Void): Unit = promise.success(())
        override def onFail(ex: Exception): Unit   = promise.failure(ex)
      }
    )

    promise.future
  }

  override def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit] = {
    val topicRef = client.getTopicRef(rawTopicName(topic))

    val publishMessages = messages.map { message =>
      val promise = Promise[TopicMessage]

      val topicMessage = new Base64TopicMessage
      topicMessage.setMessageBody(topic.serialize(message).array())

      topicRef.asyncPublishMessage(
        topicMessage,
        new AsyncCallback[TopicMessage] {
          override def onSuccess(result: TopicMessage): Unit = promise.success(result)
          override def onFail(ex: Exception): Unit           = promise.failure(ex)
        }
      )

      promise.future
    }

    Future.sequence(publishMessages).map(_ => ())
  }

  def createTopic(topic: Topic[_]): Future[Unit] = Future {
    val topicName   = rawTopicName(topic)
    val topicExists = Option(client.listTopic(topicName, "", 1)).exists(!_.getResult.isEmpty)
    if (!topicExists) {
      val topicMeta = new TopicMeta
      topicMeta.setTopicName(topicName)
      client.createTopic(topicMeta)
    }
  }

  def createSubscription(topic: Topic[_], config: SubscriptionConfig): Future[Unit] = Future {
    val subscriptionName = rawSubscriptionName(config, topic)
    val topicName        = rawTopicName(topic)
    val topicRef         = client.getTopicRef(topicName)

    val queueMeta = new QueueMeta
    queueMeta.setQueueName(subscriptionName)
    queueMeta.setVisibilityTimeout(config.ackTimeout.toSeconds)
    client.createQueue(queueMeta)

    val subscriptionMeta = new SubscriptionMeta
    subscriptionMeta.setSubscriptionName(subscriptionName)
    subscriptionMeta.setTopicName(topicName)
    subscriptionMeta.setEndpoint(topicRef.generateQueueEndpoint(subscriptionName))
    topicRef.subscribe(subscriptionMeta)
  }
}
