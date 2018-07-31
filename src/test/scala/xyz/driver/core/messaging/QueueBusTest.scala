package xyz.driver.core.messaging

import akka.actor.ActorSystem
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class QueueBusTest extends FlatSpec with ScalaFutures {
  implicit val patience: PatienceConfig = PatienceConfig(timeout = 10.seconds)

  def busBehaviour(bus: Bus)(implicit ec: ExecutionContext): Unit = {

    it should "deliver messages to a subscriber" in {
      val topic = Topic.string("test.topic1")
      bus.fetchMessages(topic).futureValue
      bus.publishMessages(topic, Seq("hello world!"))
      Thread.sleep(100)
      val messages = bus.fetchMessages(topic)
      assert(messages.futureValue.map(_.data).toList == List("hello world!"))
    }
  }

  implicit val system: ActorSystem = ActorSystem("queue-test")
  import system.dispatcher

  "A queue-based bus" should behave like busBehaviour(new QueueBus)

}
