package com.drivergrp.core

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.UUID._

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{Bucket, GetObjectRequest, ListObjectsV2Request}
import com.drivergrp.core.time.Time

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

object file {

  final case class FileLink(
      id: Id[File],
      name: Name[File],
      location: Path,
      additionDate: Time
  )

  trait FileService {

    def getFileLink(id: Id[File]): FileLink

    def getFile(fileLink: FileLink): File
  }

  trait FileStorage {

    def upload(localSource: File, destination: Path): Future[Unit]

    def download(filePath: Path): OptionT[Future, File]

    def delete(filePath: Path): Future[Unit]

    def list(path: Path): ListT[Future, Name[File]]

    /** List of characters to avoid in S3 (I would say file names in general)
      *
      * @see http://stackoverflow.com/questions/7116450/what-are-valid-s3-key-names-that-can-be-accessed-via-the-s3-rest-api
      */
    private val illegalChars = "\\^`><{}][#%~|&@:,$=+?; "

    protected def checkSafeFileName[T](filePath: Path)(f: => T): T = {
      filePath.toString.find(c => illegalChars.contains(c)) match {
        case Some(illegalCharacter) =>
          throw new IllegalArgumentException(s"File name cannot contain character `$illegalCharacter`")
        case None => f
      }
    }
  }

  class S3Storage(s3: AmazonS3, bucket: Name[Bucket], executionContext: ExecutionContext) extends FileStorage {
    implicit private val execution = executionContext

    def upload(localSource: File, destination: Path): Future[Unit] = Future {
      checkSafeFileName(destination) {
        val _ = s3.putObject(bucket, destination.toString, localSource).getETag
      }
    }

    def download(filePath: Path): OptionT[Future, File] = OptionT.optionT(Future {
      val tempDir             = System.getProperty("java.io.tmpdir")
      val randomFolderName    = randomUUID().toString
      val tempDestinationFile = new File(Paths.get(tempDir, randomFolderName, filePath.toString).toString)

      if (!tempDestinationFile.getParentFile.mkdirs()) {
        throw new Exception(s"Failed to create temp directory to download file `$file`")
      } else {
        Option(s3.getObject(new GetObjectRequest(bucket, filePath.toString), tempDestinationFile)).map { _ =>
          tempDestinationFile
        }
      }
    })

    def delete(filePath: Path): Future[Unit] = Future {
      s3.deleteObject(bucket, filePath.toString)
    }

    def list(path: Path): ListT[Future, Name[File]] =
      ListT.listT(Future {
        import scala.collection.JavaConverters._
        val req = new ListObjectsV2Request().withBucketName(bucket).withPrefix(path.toString).withMaxKeys(2)

        Iterator.continually(s3.listObjectsV2(req)).takeWhile { result =>
          req.setContinuationToken(result.getNextContinuationToken)
          result.isTruncated
        } flatMap { result =>
          result.getObjectSummaries.asScala
            .map(_.getKey)
            .toList
            .filterNot(_.replace(path.toString + "/", "").contains("/")) // filter out sub-folders or files in sub-folders
            .map(item => Name[File](new File(item).getName))
        } toList
      })
  }

  class FileSystemStorage(executionContext: ExecutionContext) extends FileStorage {
    implicit private val execution = executionContext

    def upload(localSource: File, destination: Path): Future[Unit] = Future {
      checkSafeFileName(destination) {
        val destinationFile = destination.toFile

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

    def download(filePath: Path): OptionT[Future, File] = OptionT.optionT(Future {
      Option(new File(filePath.toString)).filter(file => file.exists() && file.isFile)
    })

    def delete(filePath: Path): Future[Unit] = Future {
      val file = new File(filePath.toString)
      if (file.delete()) ()
      else {
        throw new Exception(s"Failed to delete file $file" + (if (!file.exists()) ", file does not exist." else "."))
      }
    }

    def list(path: Path): ListT[Future, Name[File]] =
      ListT.listT(Future {
        val file = new File(path.toString)
        if (file.isDirectory) file.listFiles().filter(_.isFile).map(f => Name[File](f.getName)).toList
        else List.empty[Name[File]]
      })
  }
}
