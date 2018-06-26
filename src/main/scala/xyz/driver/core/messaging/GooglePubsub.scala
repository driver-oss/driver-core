package xyz.driver.core.messaging

import java.nio.file.{Files, Path, Paths}

import akka.util.ByteString
import com.google.api.core.ApiFutures
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.{ServiceAccountCredentials, UserCredentials}
import com.google.cloud.pubsub.v1._
import com.google.protobuf.{ByteString => PByteString}
import com.google.pubsub.v1.{ProjectSubscriptionName, ProjectTopicName, PubsubMessage, PushConfig}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class GooglePubsub(credentials: ServiceAccountCredentials)(implicit ec: ExecutionContext) extends MessageBus {

  private lazy val subscriptionAdmin = {
    val settings = SubscriptionAdminSettings
      .newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
      .build()
    SubscriptionAdminClient.create(settings)
  }

  override def handle[A](topic: Topic[A], uniqueId: Option[String], timeout: FiniteDuration)(
      action: PartialFunction[A, Future[Any]]): Future[Subscription] = Future {
    val subscriptionId = uniqueId.getOrElse("default_subscription." + topic.name)

    val subscriptionName = ProjectSubscriptionName.of(credentials.getProjectId, subscriptionId)
    val topicName        = ProjectTopicName.of(credentials.getProjectId, topic.name)

    val subscription = try {
      subscriptionAdmin
        .createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), timeout.toSeconds.toInt)
    } catch {
      case NonFatal(e) => throw new RuntimeException("Error creating subscription", e.getCause)
    }

    val receiver = new MessageReceiver() {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer): Unit = {
        val data = ByteString(message.getData.asReadOnlyByteBuffer())
        val future = topic.read(data).flatMap { message =>
          if (action.isDefinedAt(message)) {
            action(message)
          } else {
            Future.unit
          }
        }
        future.onComplete {
          case Success(_) =>
            println("ack")
            consumer.ack()
          case Failure(_) =>
            println("nack")
            consumer.nack()
        }
      }
    }

    val subscriber = Subscriber.newBuilder(subscription.getName, receiver).build()
    val instance   = subscriber.startAsync()

    new Subscription {
      override def cancel(): Unit = instance.stopAsync()
    }
  }

  override def send[A](topic: Topic[A], messages: Seq[A]): Future[Seq[A]] = {
    val publisher = Future {
      val topicName = ProjectTopicName.of(credentials.getProjectId, topic.name)
      Publisher.newBuilder(topicName).setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build()
    }
    for {
      pub     <- publisher
      buffers <- Future.sequence(messages.map(topic.write))
    } yield {
      val futures = buffers.map { buffer =>
        val msg = PubsubMessage.newBuilder().setData(PByteString.copyFrom(buffer.asByteBuffer)).build()
        pub.publish(msg)
      }
      ApiFutures.allAsList(futures.asJava).get()
      pub.shutdown()
      messages
    }
  }

}

object GooglePubsub {

  def fromEnv(implicit ec: ExecutionContext): GooglePubsub = {
    val key = "GOOGLE_APPLICATION_CREDENTIALS"
    require(sys.env.contains(key), s"Environment variable $key is not set.")
    fromKeyfile(Paths.get(sys.env(key)))
  }

  def fromKeyfile(file: Path)(implicit ec: ExecutionContext): GooglePubsub = {
    new GooglePubsub(ServiceAccountCredentials.fromStream(Files.newInputStream(file)))
  }

}
