package xyz.driver.core.file

import java.io.{BufferedOutputStream, File, FileInputStream, FileOutputStream}
import java.nio.file.{Path, Paths}

import com.google.cloud.storage.Storage.BlobListOption
import com.google.cloud.storage._
import xyz.driver.core.time.Time
import xyz.driver.core.{Name, Revision, generators}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

class GcsStorage(storageClient: Storage, bucketName: Name[Bucket], executionContext: ExecutionContext)
    extends FileStorage {
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

  override def list(path: Path): ListT[Future, FileLink] =
    ListT.listT(Future {
      val page = storageClient.list(
        bucketName.value,
        BlobListOption.currentDirectory(),
        BlobListOption.prefix(path.toString)
      )

      page.iterateAll().asScala.map(blobToFileLink(path, _)).toList
    })

  protected def blobToFileLink(path: Path, blob: Blob): FileLink = {
    FileLink(
      Name(blob.getName),
      Paths.get(path.toString, blob.getName),
      Revision(blob.getGeneration.toString),
      Time(blob.getUpdateTime),
      blob.getSize
    )
  }
}
