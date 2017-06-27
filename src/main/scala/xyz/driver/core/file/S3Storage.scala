package xyz.driver.core.file

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID.randomUUID

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{Bucket, GetObjectRequest, ListObjectsV2Request}
import xyz.driver.core.{Name, Revision}
import xyz.driver.core.time.Time

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

class S3Storage(s3: AmazonS3, bucket: Name[Bucket], executionContext: ExecutionContext, chunkSize: Int = 4096)
    extends FileStorage {
  implicit private val execution = executionContext

  override def upload(localSource: File, destination: Path): Future[Unit] = Future {
    checkSafeFileName(destination) {
      val _ = s3.putObject(bucket.value, destination.toString, localSource).getETag
    }
  }

  override def download(filePath: Path): OptionT[Future, File] =
    OptionT.optionT(Future {
      val tempDir             = System.getProperty("java.io.tmpdir")
      val randomFolderName    = randomUUID().toString
      val tempDestinationFile = new File(Paths.get(tempDir, randomFolderName, filePath.toString).toString)

      if (!tempDestinationFile.getParentFile.mkdirs()) {
        throw new Exception(s"Failed to create temp directory to download file `$tempDestinationFile`")
      } else {
        Option(s3.getObject(new GetObjectRequest(bucket.value, filePath.toString), tempDestinationFile)).map { _ =>
          tempDestinationFile
        }
      }
    })

  override def stream(filePath: Path): OptionT[Future, Source[ByteString, NotUsed]] =
    OptionT.optionT(Future {
      Option(s3.getObject(new GetObjectRequest(bucket.value, filePath.toString))).map { elem =>
        StreamConverters.fromInputStream(() => elem.getObjectContent(), chunkSize).mapMaterializedValue(_ => NotUsed)
      }
    })

  override def delete(filePath: Path): Future[Unit] = Future {
    s3.deleteObject(bucket.value, filePath.toString)
  }

  override def list(path: Path): ListT[Future, FileLink] =
    ListT.listT(Future {
      import scala.collection.JavaConverters._
      val req = new ListObjectsV2Request().withBucketName(bucket.value).withPrefix(path.toString).withMaxKeys(2)

      def isInSubFolder(path: Path)(fileLink: FileLink) =
        fileLink.location.toString.replace(path.toString + "/", "").contains("/")

      Iterator.continually(s3.listObjectsV2(req)).takeWhile { result =>
        req.setContinuationToken(result.getNextContinuationToken)
        result.isTruncated
      } flatMap { result =>
        result.getObjectSummaries.asScala.toList.map { summary =>
          FileLink(
            Name[File](summary.getKey),
            Paths.get(path.toString + "/" + summary.getKey),
            Revision[File](summary.getETag),
            Time(summary.getLastModified.getTime),
            summary.getSize
          )
        } filterNot isInSubFolder(path)
      } toList
    })

  override def exists(path: Path): Future[Boolean] = Future {
    s3.doesObjectExist(bucket.value, path.toString)
  }

}
