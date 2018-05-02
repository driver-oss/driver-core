package xyz.driver.core.messaging

import akka.util.ByteString

import scala.concurrent.Future

trait Topic[To] {
  def name: String
  def read: ByteString => Future[To]
  def write: To => Future[ByteString]
}

object Topic {
  implicit def namedTopic(name0: String): Topic[ByteString] = new Topic[ByteString] {
    val name  = name0
    val read  = (a: ByteString) => Future.successful(a)
    val write = (a: ByteString) => Future.successful(a)
  }
}
