package xyz.driver.core
package messaging

import java.nio.ByteBuffer

/** A topic is a named group of messages that all share a common schema.
  * @tparam Message type of messages sent over this topic */
trait Topic[Message] {

  /** Name of this topic (must be unique). */
  def name: String

  /** Convert a message to its wire format that will be sent over a bus. */
  def serialize(message: Message): ByteBuffer

  /** Convert a message from its wire format. */
  def deserialize(message: ByteBuffer): Message

}

object Topic {

  /** Create a new "raw" topic without a schema, providing access to the underlying bytes of messages. */
  def raw(name0: String): Topic[ByteBuffer] = new Topic[ByteBuffer] {
    def name                                                  = name0
    override def serialize(message: ByteBuffer): ByteBuffer   = message
    override def deserialize(message: ByteBuffer): ByteBuffer = message
  }

  /** Create a topic that represents data as UTF-8 encoded strings. */
  def string(name0: String): Topic[String] = new Topic[String] {
    def name = name0
    override def serialize(message: String): ByteBuffer = {
      ByteBuffer.wrap(message.getBytes("utf-8"))
    }
    override def deserialize(message: ByteBuffer): String = {
      val bytes = new Array[Byte](message.remaining())
      message.get(bytes)
      new String(bytes, "utf-8")
    }
  }

}
