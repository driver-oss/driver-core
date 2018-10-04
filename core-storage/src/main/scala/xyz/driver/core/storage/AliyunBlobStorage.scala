package xyz.driver.core.storage

import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.Path
import java.time.Clock
import java.util.Date

import akka.Done
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import com.aliyun.oss.OSSClient
import com.aliyun.oss.model.ObjectPermission
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class AliyunBlobStorage(
    client: OSSClient,
    bucketId: String,
    clock: Clock,
    chunkSize: Int = AliyunBlobStorage.DefaultChunkSize)(implicit ec: ExecutionContext)
    extends SignedBlobStorage {
  override def uploadContent(name: String, content: Array[Byte]): Future[String] = Future {
    client.putObject(bucketId, name, new ByteArrayInputStream(content))
    name
  }

  override def uploadFile(name: String, content: Path): Future[String] = Future {
    client.putObject(bucketId, name, content.toFile)
    name
  }

  override def exists(name: String): Future[Boolean] = Future {
    client.doesObjectExist(bucketId, name)
  }

  override def list(prefix: String): Future[Set[String]] = Future {
    client.listObjects(bucketId, prefix).getObjectSummaries.asScala.map(_.getKey)(collection.breakOut)
  }

  override def content(name: String): Future[Option[Array[Byte]]] = Future {
    Option(client.getObject(bucketId, name)).map { obj =>
      val inputStream = obj.getObjectContent
      Stream.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
    }
  }

  override def download(name: String): Future[Option[Source[ByteString, Any]]] = Future {
    Option(client.getObject(bucketId, name)).map { obj =>
      StreamConverters.fromInputStream(() => obj.getObjectContent, chunkSize)
    }
  }

  override def upload(name: String): Future[Sink[ByteString, Future[Done]]] = Future {
    StreamConverters
      .asInputStream()
      .mapMaterializedValue(is =>
        Future {
          client.putObject(bucketId, name, is)
          Done
      })
  }

  override def delete(name: String): Future[String] = Future {
    client.deleteObject(bucketId, name)
    name
  }

  override def url(name: String): Future[Option[URL]] = Future {
    // Based on https://www.alibabacloud.com/help/faq-detail/39607.htm
    Option(client.getObjectAcl(bucketId, name)).map { acl =>
      val isPrivate   = acl.getPermission == ObjectPermission.Private
      val bucket      = client.getBucketInfo(bucketId).getBucket
      val endpointUrl = if (isPrivate) bucket.getIntranetEndpoint else bucket.getExtranetEndpoint
      new URL(s"https://$bucketId.$endpointUrl/$name")
    }
  }

  override def signedDownloadUrl(name: String, duration: Duration): Future[Option[URL]] = Future {
    if (client.doesObjectExist(bucketId, name)) {
      val expiration = new Date(clock.millis() + duration.toMillis)
      Some(client.generatePresignedUrl(bucketId, name, expiration))
    } else {
      None
    }
  }
}

object AliyunBlobStorage {
  val DefaultChunkSize: Int = 8192

  def apply(config: Config, bucketId: String, clock: Clock)(implicit ec: ExecutionContext): AliyunBlobStorage = {
    val clientId     = config.getString("storage.aliyun.clientId")
    val clientSecret = config.getString("storage.aliyun.clientSecret")
    val endpoint     = config.getString("storage.aliyun.endpoint")
    this(clientId, clientSecret, endpoint, bucketId, clock)
  }

  def apply(clientId: String, clientSecret: String, endpoint: String, bucketId: String, clock: Clock)(
      implicit ec: ExecutionContext): AliyunBlobStorage = {
    val client = new OSSClient(endpoint, clientId, clientSecret)
    new AliyunBlobStorage(client, bucketId, clock)
  }
}
