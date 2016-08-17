package com.drivergrp.core

import java.io.File
import java.nio.file.Paths
import java.util.UUID._

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{Bucket, GetObjectRequest, ListObjectsV2Request}
import com.drivergrp.core.time.Time

import scala.concurrent.{ExecutionContext, Future}
import scalaz.ListT

object file {

  final case class FilePath(path: String)

  final case class FileLink(
      id: Id[File],
      name: Name[File],
      location: FilePath,
      additionDate: Time
  )

  trait FileService {

    def getFileLink(id: Id[File]): FileLink

    def getFile(fileLink: FileLink): File
  }

  trait FileStorage {

    def upload(localSource: File, destination: FilePath): Future[Unit]

    def download(filePath: FilePath): Future[File]

    def delete(filePath: FilePath): Future[Unit]

    def list(path: FilePath): ListT[Future, Name[File]]

    /** List of characters to avoid in S3 (I would say file names in general)
      *
      * @see http://stackoverflow.com/questions/7116450/what-are-valid-s3-key-names-that-can-be-accessed-via-the-s3-rest-api
      */
    private val illegalChars = "\\^`><{}][#%~|&@:,$=+?; "

    protected def checkSafeFileName[T](filePath: FilePath)(f: => T): T = {
      filePath.path.find(c => illegalChars.contains(c)) match {
        case Some(illegalCharacter) =>
          throw new IllegalArgumentException(s"File name cannot contain character `$illegalCharacter`")
        case None => f
      }
    }
  }

  class S3Storage(s3: AmazonS3, bucket: Name[Bucket], executionContext: ExecutionContext) extends FileStorage {
    implicit private val execution = executionContext

    def upload(localSource: File, destination: FilePath): Future[Unit] = Future {
      checkSafeFileName(destination) {
        val _ = s3.putObject(bucket, destination.path, localSource).getETag
      }
    }

    def download(filePath: FilePath): Future[File] = Future {
      val tempDir             = System.getProperty("java.io.tmpdir")
      val randomFolderName    = randomUUID().toString
      val tempDestinationFile = new File(Paths.get(tempDir, randomFolderName, filePath.path).toString)

      if (!tempDestinationFile.getParentFile.mkdirs()) {
        throw new Exception(s"Failed to create temp directory to download file `$file`")
      } else {
        val _ = s3.getObject(new GetObjectRequest(bucket, filePath.path), tempDestinationFile)
        tempDestinationFile
      }
    }

    def delete(filePath: FilePath): Future[Unit] = Future {
      s3.deleteObject(bucket, filePath.path)
    }

    def list(path: FilePath): ListT[Future, Name[File]] =
      ListT.listT(Future {
        import scala.collection.JavaConverters._
        val req = new ListObjectsV2Request().withBucketName(bucket).withPrefix(path.path).withMaxKeys(2)

        Iterator.continually(s3.listObjectsV2(req)).takeWhile { result =>
          req.setContinuationToken(result.getNextContinuationToken)
          result.isTruncated
        } flatMap { result =>
          result.getObjectSummaries.asScala
            .map(_.getKey)
            .toList
            .filterNot(_.replace(path.path + "/", "").contains("/")) // filter out sub-folders or files in sub-folders
            .map(item => Name[File](new File(item).getName))
        } toList
      })
  }

  class FileSystemStorage(executionContext: ExecutionContext) extends FileStorage {
    implicit private val execution = executionContext

    def upload(localSource: File, destination: FilePath): Future[Unit] = Future {
      checkSafeFileName(destination) {
        val destinationFile = Paths.get(destination.path).toFile

        if (destinationFile.getParentFile.exists() || destinationFile.getParentFile.mkdirs()) {
          if (localSource.renameTo(destinationFile)) ()
          else {
            throw new Exception(
                s"Failed to move file from `${localSource.getCanonicalPath}` to `${destinationFile.getCanonicalPath}`")
          }
        } else {
          throw new Exception(s"Failed to create parent directories for file `${destinationFile.getCanonicalPath}`")
        }
      }
    }

    def download(filePath: FilePath): Future[File] = Future {
      make(new File(filePath.path)) { file =>
        assert(file.exists() && file.isFile)
      }
    }

    def delete(filePath: FilePath): Future[Unit] = Future {
      val file = new File(filePath.path)
      if (file.delete()) ()
      else {
        throw new Exception(s"Failed to delete file $file" + (if (!file.exists()) ", file does not exist." else "."))
      }
    }

    def list(path: FilePath): ListT[Future, Name[File]] =
      ListT.listT(Future {
        val file = new File(path.path)
        if (file.isDirectory) file.listFiles().filter(_.isFile).map(f => Name[File](f.getName)).toList
        else List.empty[Name[File]]
      })
  }
}
