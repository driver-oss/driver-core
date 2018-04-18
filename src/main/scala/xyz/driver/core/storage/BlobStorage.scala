package xyz.driver.core.storage

import java.net.URL
import java.nio.file.Path

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

/** Binary key-value store, typically implemented by cloud storage. */
trait BlobStorage {
  /** Upload data by value. */
  def uploadContent(name: String, content: Array[Byte]): Future[String]

  /** Upload data from an existing file. */
  def uploadFile(name: String, content: Path): Future[String]

  def exists(name: String): Future[Boolean]

  /** List available keys. The prefix determines which keys should be listed
    * and depends on the implementation (for instance, a file system backed
    * blob store will treat a prefix as a directory path). */
  def list(prefix: String): Future[Set[String]]

  /** Get all the content of a given object. */
  def content(name: String): Future[Option[Array[Byte]]]

  /** Stream data asynchronously and with backpressure. */
  def download(name: String): Future[Option[Source[ByteString, NotUsed]]]

  /** Get a sink to upload data. */
  def upload(name: String): Future[Sink[ByteString, Future[Done]]]

  /** Delete a stored value. */
  def delete(name: String): Future[String]

  /**
    * Path to specified resource. Checks that the resource exists and returns None if
    * it is not found. Depending on the implementation, may throw.
    */
  def url(name: String): Future[Option[URL]]
}

trait SignedBlobStorage extends BlobStorage {
  def signedDownloadUrl(name: String, duration: Duration): Future[Option[URL]]
}
