package xyz.driver.core.file

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import java.io.File
import java.nio.file.{Files, Path, Paths}

import xyz.driver.core.{Name, Revision}
import xyz.driver.core.time.Time

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

class FileSystemStorage(executionContext: ExecutionContext) extends FileStorage {
  implicit private val execution = executionContext

  override def upload(localSource: File, destination: Path): Future[Unit] = Future {
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

  override def download(filePath: Path): OptionT[Future, File] =
    OptionT.optionT(Future {
      Option(new File(filePath.toString)).filter(file => file.exists() && file.isFile)
    })

  override def stream(filePath: Path): OptionT[Future, Source[ByteString, NotUsed]] =
    OptionT.optionT(Future {
      if (Files.exists(filePath)) {
        Some(FileIO.fromPath(filePath).mapMaterializedValue(_ => NotUsed))
      } else {
        None
      }
    })

  override def delete(filePath: Path): Future[Unit] = Future {
    val file = new File(filePath.toString)
    if (file.delete()) ()
    else {
      throw new Exception(s"Failed to delete file $file" + (if (!file.exists()) ", file does not exist." else "."))
    }
  }

  override def list(path: Path): ListT[Future, FileLink] =
    ListT.listT(Future {
      val file = new File(path.toString)
      if (file.isDirectory) {
        file.listFiles().toList.filter(_.isFile).map { file =>
          FileLink(Name[File](file.getName),
                   Paths.get(file.getPath),
                   Revision[File](file.hashCode.toString),
                   Time(file.lastModified()),
                   file.length())
        }
      } else List.empty[FileLink]
    })

  override def exists(path: Path): Future[Boolean] = Future {
    Files.exists(path)
  }

}
