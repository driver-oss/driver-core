package xyz.driver.core.storage

import java.io.{FileInputStream, InputStream}
import java.net.URL
import java.nio.file.Path

import akka.Done
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.google.api.gax.paging.Page
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage.{Blob, BlobId, Bucket, Storage, StorageOptions}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class GcsBlobStorage(client: Storage, bucketId: String, chunkSize: Int = GcsBlobStorage.DefaultChunkSize)(
    implicit ec: ExecutionContext)
    extends BlobStorage with SignedBlobStorage {

  private val bucket: Bucket = client.get(bucketId)
  require(bucket != null, s"Bucket $bucketId does not exist.")

  override def uploadContent(name: String, content: Array[Byte]): Future[String] = Future {
    bucket.create(name, content).getBlobId.getName
  }

  override def uploadFile(name: String, content: Path): Future[String] = Future {
    bucket.create(name, new FileInputStream(content.toFile)).getBlobId.getName
  }

  override def exists(name: String): Future[Boolean] = Future {
    bucket.get(name) != null
  }

  override def list(prefix: String): Future[Set[String]] = Future {
    val page: Page[Blob] = bucket.list(BlobListOption.prefix(prefix))
    page
      .iterateAll()
      .asScala
      .map(_.getName())
      .toSet
  }

  override def content(name: String): Future[Option[Array[Byte]]] = Future {
    Option(bucket.get(name)).map(blob => blob.getContent())
  }

  override def download(name: String) = Future {
    Option(bucket.get(name)).map { blob =>
      ChannelStream.fromChannel(() => blob.reader(), chunkSize)
    }
  }

  override def upload(name: String): Future[Sink[ByteString, Future[Done]]] = Future {
    val blob = bucket.create(name, Array.emptyByteArray)
    ChannelStream.toChannel(() => blob.writer(), chunkSize)
  }

  override def delete(name: String): Future[String] = Future {
    client.delete(BlobId.of(bucketId, name))
    name
  }

  override def signedDownloadUrl(name: String, duration: Duration): Future[Option[URL]] = Future {
    Option(bucket.get(name)).map(blob => blob.signUrl(duration.length, duration.unit))
  }

  override def url(name: String): Future[Option[URL]] = Future {
    val protocol: String = "https"
    val resourcePath: String = s"storage.googleapis.com/${bucket.getName}/"
    Option(bucket.get(name)).map { blob =>
      new URL(protocol, resourcePath, blob.getName)
    }
  }
}

object GcsBlobStorage {
  final val DefaultChunkSize = 8192

  private def newClient(key: InputStream): Storage =
    StorageOptions
      .newBuilder()
      .setCredentials(ServiceAccountCredentials.fromStream(key))
      .build()
      .getService()

  def fromKeyfile(keyfile: Path, bucketId: String, chunkSize: Int = DefaultChunkSize)(
      implicit ec: ExecutionContext): GcsBlobStorage = {
    val client = newClient(new FileInputStream(keyfile.toFile))
    new GcsBlobStorage(client, bucketId, chunkSize)
  }

}
