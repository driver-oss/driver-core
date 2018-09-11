package xyz.driver.core
package messaging

import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.security.Signature
import java.time.Instant
import java.util

import com.google.auth.oauth2.ServiceAccountCredentials
import com.softwaremill.sttp._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.async.Async.{async, await}
import scala.concurrent._
import scala.concurrent.duration._

/** A message bus implemented by [[https://cloud.google.com/pubsub/docs/overview Google's Pub/Sub service.]]
  *
  * == Overview ==
  *
  * The Pub/Sub message system is focused around a few concepts: 'topics',
  * 'subscriptions' and 'subscribers'. Messages are sent to ''topics'' which may
  * have multiple ''subscriptions'' associated to them. Every subscription to a
  * topic will receive all messages sent to the topic.  Messages are enqueued in
  * a subscription until they are acknowledged by a ''subscriber''.  Multiple
  * subscribers may be associated to a subscription, in which case messages will
  * get delivered arbitrarily among them.
  *
  * Topics and subscriptions are named resources which can be specified in
  * Pub/Sub's configuration and may be queried. Subscribers on the other hand,
  * are ephemeral processes that query a subscription on a regular basis, handle any
  * messages and acknowledge them.
  *
  * == Delivery semantics ==
  *
  *   - at least once
  *   - no ordering
  *
  * == Retention ==
  *
  *   - configurable retry delay for unacknowledged messages, defaults to 10s
  *   - undeliverable messages are kept for 7 days
  *
  * @param credentials Google cloud credentials, usually the same as used by a
  *                    service. Must have admin access to topics and
  *                    descriptions.
  * @param namespace The namespace in which this bus is running. Will be used to
  *                  determine the exact name of topics and subscriptions.
  * @param pullTimeout Delay after which a call to fetchMessages() will return an
  *                    empty list, assuming that no messages have been received.
  * @param executionContext Execution context to run any blocking commands.
  * @param backend sttp backend used to query Pub/Sub's HTTP API
  */
class GoogleBus(
    credentials: ServiceAccountCredentials,
    namespace: String,
    pullTimeout: Duration = 90.seconds
)(implicit val executionContext: ExecutionContext, backend: SttpBackend[Future, _])
    extends Bus with StreamBus with CreateBeforeStream {
  import GoogleBus.Protocol

  case class MessageId(subscription: String, ackId: String)

  case class PubsubMessage[A](id: MessageId, data: A, publishTime: Instant) extends super.BasicMessage[A]
  type Message[A] = PubsubMessage[A]

  /** Subscription-specific configuration
    *
    * @param subscriptionPrefix An identifier used to uniquely determine the name of Pub/Sub subscriptions.
    *                           All messages sent to a subscription will be dispatched arbitrarily
    *                           among any subscribers. Defaults to the email of the credentials used by this
    *                           bus instance, thereby giving every service a unique subscription to every topic.
    *                           To give every service instance a unique subscription, this must be changed to a
    *                           unique value.
    * @param ackTimeout Duration in which a message must be acknowledged before it is delivered again.
    */
  case class SubscriptionConfig(
      subscriptionPrefix: String = credentials.getClientEmail.split("@")(0),
      ackTimeout: FiniteDuration = 10.seconds
  )
  override val defaultSubscriptionConfig: SubscriptionConfig = SubscriptionConfig()

  /** Obtain an authentication token valid for the given duration
    * https://developers.google.com/identity/protocols/OAuth2ServiceAccount
    */
  private def freshAuthToken(duration: FiniteDuration): Future[String] = {
    def jwt = {
      val now    = Instant.now().getEpochSecond
      val base64 = util.Base64.getEncoder
      val header = base64.encodeToString("""{"alg":"RS256","typ":"JWT"}""".getBytes("utf-8"))
      val body = base64.encodeToString(
        s"""|{
            | "iss": "${credentials.getClientEmail}",
            | "scope": "https://www.googleapis.com/auth/pubsub",
            | "aud": "https://www.googleapis.com/oauth2/v4/token",
            | "exp": ${now + duration.toSeconds},
            | "iat": $now
            |}""".stripMargin.getBytes("utf-8")
      )
      val signer = Signature.getInstance("SHA256withRSA")
      signer.initSign(credentials.getPrivateKey)
      signer.update(s"$header.$body".getBytes("utf-8"))
      val signature = base64.encodeToString(signer.sign())
      s"$header.$body.$signature"
    }
    sttp
      .post(uri"https://www.googleapis.com/oauth2/v4/token")
      .body(
        "grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion"  -> jwt
      )
      .mapResponse(s => s.parseJson.asJsObject.fields("access_token").convertTo[String])
      .send()
      .map(_.unsafeBody)
  }

  // the token is cached a few minutes less than its validity to diminish latency of concurrent accesses at renewal time
  private val getToken: () => Future[String] = Refresh.every[String](55.minutes)(freshAuthToken(60.minutes))

  private val baseUri = uri"https://pubsub.googleapis.com/"

  private def rawTopicName(topic: Topic[_]) =
    s"projects/${credentials.getProjectId}/topics/$namespace.${topic.name}"
  private def rawSubscriptionName(config: SubscriptionConfig, topic: Topic[_]) =
    s"projects/${credentials.getProjectId}/subscriptions/$namespace.${config.subscriptionPrefix}.${topic.name}"

  def createTopic(topic: Topic[_]): Future[Unit] = async {
    val request = sttp
      .put(baseUri.path(s"v1/${rawTopicName(topic)}"))
      .auth
      .bearer(await(getToken()))
    val result = await(request.send())
    result.body match {
      case Left(error) if result.code != 409 => // 409 <=> topic already exists
        throw new NoSuchElementException(s"Error creating topic: Status code ${result.code}: $error")
      case _ => ()
    }
  }

  def createSubscription(topic: Topic[_], config: SubscriptionConfig): Future[Unit] = async {
    val request = sttp
      .put(baseUri.path(s"v1/${rawSubscriptionName(config, topic)}"))
      .auth
      .bearer(await(getToken()))
      .body(
        JsObject(
          "topic"              -> rawTopicName(topic).toJson,
          "ackDeadlineSeconds" -> config.ackTimeout.toSeconds.toJson
        ).compactPrint
      )
    val result = await(request.send())
    result.body match {
      case Left(error) if result.code != 409 => // 409 <=> subscription already exists
        throw new NoSuchElementException(s"Error creating subscription: Status code ${result.code}: $error")
      case _ => ()
    }
  }

  override def publishMessages[A](topic: Topic[A], messages: Seq[A]): Future[Unit] = async {
    import Protocol.bufferFormat
    val buffers: Seq[ByteBuffer] = messages.map(topic.serialize)
    val request = sttp
      .post(baseUri.path(s"v1/${rawTopicName(topic)}:publish"))
      .auth
      .bearer(await(getToken()))
      .body(
        JsObject("messages" -> buffers.map(buffer => JsObject("data" -> buffer.toJson)).toJson).compactPrint
      )
    await(request.send()).unsafeBody
    ()
  }

  override def fetchMessages[A](
      topic: Topic[A],
      subscriptionConfig: SubscriptionConfig,
      maxMessages: Int): Future[Seq[PubsubMessage[A]]] = async {
    val subscription = rawSubscriptionName(subscriptionConfig, topic)
    val request = sttp
      .post(baseUri.path(s"v1/$subscription:pull"))
      .auth
      .bearer(await(getToken().map(x => x)))
      .body(
        JsObject(
          "returnImmediately" -> JsFalse,
          "maxMessages"       -> JsNumber(maxMessages)
        ).compactPrint
      )
      .readTimeout(pullTimeout)
      .mapResponse(_.parseJson)

    val messages = await(request.send()).unsafeBody match {
      case JsObject(fields) if fields.isEmpty => Seq()
      case obj                                => obj.convertTo[Protocol.SubscriptionPull].receivedMessages
    }

    messages.map { msg =>
      PubsubMessage[A](
        MessageId(subscription, msg.ackId),
        topic.deserialize(msg.message.data),
        msg.message.publishTime
      )
    }
  }

  override def acknowledgeMessages(messageIds: Seq[MessageId]): Future[Unit] = async {
    val request = sttp
      .post(baseUri.path(s"v1/${messageIds.head.subscription}:acknowledge"))
      .auth
      .bearer(await(getToken()))
      .body(
        JsObject("ackIds" -> JsArray(messageIds.toVector.map(m => JsString(m.ackId)))).compactPrint
      )
    await(request.send()).unsafeBody
    ()
  }

}

object GoogleBus {

  private object Protocol extends DefaultJsonProtocol {
    case class SubscriptionPull(receivedMessages: Seq[ReceivedMessage])
    case class ReceivedMessage(ackId: String, message: PubsubMessage)
    case class PubsubMessage(data: ByteBuffer, publishTime: Instant)

    implicit val timeFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
      override def write(obj: Instant): JsValue = JsString(obj.toString)
      override def read(json: JsValue): Instant = Instant.parse(json.convertTo[String])
    }
    implicit val bufferFormat: JsonFormat[ByteBuffer] = new JsonFormat[ByteBuffer] {
      override def write(obj: ByteBuffer): JsValue =
        JsString(util.Base64.getEncoder.encodeToString(obj.array()))

      override def read(json: JsValue): ByteBuffer = {
        val encodedBytes = json.convertTo[String].getBytes("utf-8")
        val decodedBytes = util.Base64.getDecoder.decode(encodedBytes)
        ByteBuffer.wrap(decodedBytes)
      }
    }

    implicit val pubsubMessageFormat: RootJsonFormat[PubsubMessage]      = jsonFormat2(PubsubMessage)
    implicit val receivedMessageFormat: RootJsonFormat[ReceivedMessage]  = jsonFormat2(ReceivedMessage)
    implicit val subscrptionPullFormat: RootJsonFormat[SubscriptionPull] = jsonFormat1(SubscriptionPull)
  }

  def fromEnv(implicit executionContext: ExecutionContext, backend: SttpBackend[Future, _]): GoogleBus = {
    def env(key: String) = {
      require(sys.env.contains(key), s"Environment variable $key is not set.")
      sys.env(key)
    }
    val keyfile = Paths.get(env("GOOGLE_APPLICATION_CREDENTIALS"))
    val creds   = ServiceAccountCredentials.fromStream(Files.newInputStream(keyfile))
    new GoogleBus(creds, env("SERVICE_NAMESPACE"))
  }

}
