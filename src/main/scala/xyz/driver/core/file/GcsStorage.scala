package xyz.driver.core.file

import java.io.{BufferedOutputStream, File, FileInputStream, FileOutputStream}
import java.net.URL
import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage._
import xyz.driver.core.time.Time
import xyz.driver.core.{Name, Revision, generators}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

class GcsStorage(storageClient: Storage, bucketName: Name[Bucket], executionContext: ExecutionContext)
    extends SignedFileStorage {
  implicit private val execution: ExecutionContext = executionContext

  override def upload(localSource: File, destination: Path): Future[Unit] = Future {
    checkSafeFileName(destination) {
      val blobId = BlobId.of(bucketName.value, destination.toString)
      def acl    = Bucket.BlobWriteOption.predefinedAcl(Storage.PredefinedAcl.PUBLIC_READ)

      storageClient.get(bucketName.value).create(blobId.getName, new FileInputStream(localSource), acl)
    }
  }

  override def download(filePath: Path): OptionT[Future, File] = {
    OptionT.optionT(Future {
      Option(storageClient.get(bucketName.value, filePath.toString)).filterNot(_.getSize == 0).map {
        blob =>
          val tempDir             = System.getProperty("java.io.tmpdir")
          val randomFolderName    = generators.nextUuid().toString
          val tempDestinationFile = new File(Paths.get(tempDir, randomFolderName, filePath.toString).toString)

          if (!tempDestinationFile.getParentFile.mkdirs()) {
            throw new Exception(s"Failed to create temp directory to download file `$tempDestinationFile`")
          } else {
            val target = new BufferedOutputStream(new FileOutputStream(tempDestinationFile))
            try target.write(blob.getContent())
            finally target.close()
            tempDestinationFile
          }
      }
    })
  }

  override def delete(filePath: Path): Future[Unit] = Future {
    storageClient.delete(BlobId.of(bucketName.value, filePath.toString))
  }

  override def list(directoryPath: Path): ListT[Future, FileLink] =
    ListT.listT(Future {
      val page = storageClient.list(
        bucketName.value,
        BlobListOption.currentDirectory(),
        BlobListOption.prefix(s"$directoryPath/")
      )

      page.iterateAll().asScala.map(blobToFileLink(directoryPath, _)).toList
    })

  protected def blobToFileLink(path: Path, blob: Blob): FileLink = {
    def nullError(property: String) = throw new IllegalStateException(s"Blob $blob at $path does not have $property")
    val name                        = Option(blob.getName).getOrElse(nullError("a name"))
    val generation                  = Option(blob.getGeneration).getOrElse(nullError("a generation"))
    val updateTime                  = Option(blob.getUpdateTime).getOrElse(nullError("an update time"))
    val size                        = Option(blob.getSize).getOrElse(nullError("a size"))

    FileLink(
      Name(name),
      Paths.get(path.toString, name),
      Revision(generation.toString),
      Time(updateTime),
      size
    )
  }

  override def exists(path: Path): Future[Boolean] = Future {
    val blob = Option(
      storageClient.get(
        bucketName.value,
        path.toString
      ))
    blob.isDefined
  }

  override def signedFileUrl(filePath: Path, duration: Duration): OptionT[Future, URL] =
    OptionT.optionT(Future {
      Option(storageClient.get(bucketName.value, filePath.toString)).filterNot(_.getSize == 0).map { blob =>
        blob.signUrl(duration.toSeconds, TimeUnit.SECONDS)
      }
    })
}
