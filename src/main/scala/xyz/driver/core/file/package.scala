package xyz.driver.core

import java.io.File
import java.nio.file.Path

import xyz.driver.core.time.Time

import scala.concurrent.Future
import scalaz.{ListT, OptionT}

package file {

  import java.net.URL

  import scala.concurrent.duration.Duration

  final case class FileLink(
          name: Name[File],
          location: Path,
          revision: Revision[File],
          lastModificationDate: Time,
          fileSize: Long
  )

  trait FileService {

    def getFileLink(id: Name[File]): FileLink

    def getFile(fileLink: FileLink): File
  }

  trait FileStorage {

    def upload(localSource: File, destination: Path): Future[Unit]

    def download(filePath: Path): OptionT[Future, File]

    def delete(filePath: Path): Future[Unit]

    /** List contents of a directory */
    def list(directoryPath: Path): ListT[Future, FileLink]

    def exists(path: Path): Future[Boolean]

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

  trait SignedFileStorage extends FileStorage {
    def signedFileUrl(filePath: Path, duration: Duration): OptionT[Future, URL]
  }
}
