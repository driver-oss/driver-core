package xyz.driver.core.storage

import java.net.URL
import java.nio.file.{Files, Path, StandardCopyOption}

import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** A blob store that is backed by a local filesystem. All objects are stored relative to the given
  * root path. Slashes ('/') in blob names are treated as usual path separators and are converted
  * to directories. */
class FileSystemBlobStorage(root: Path)(implicit ec: ExecutionContext) extends BlobStorage {

  private def ensureParents(file: Path): Path = {
    Files.createDirectories(file.getParent())
    file
  }

  private def file(name: String) = root.resolve(name)

  override val resourcePath: String = root.toString + "/" // Path doesn't add the trailing slash

  override val protocol: String = "file"

  override def uploadContent(name: String, content: Array[Byte]): Future[String] = Future {
    Files.write(ensureParents(file(name)), content)
    name
  }
  override def uploadFile(name: String, content: Path): Future[String] = Future {
    Files.copy(content, ensureParents(file(name)), StandardCopyOption.REPLACE_EXISTING)
    name
  }

  override def exists(name: String): Future[Boolean] = Future {
    val path = file(name)
    Files.exists(path) && Files.isReadable(path)
  }

  override def list(prefix: String): Future[Set[String]] = Future {
    val dir = file(prefix)
    Files
      .list(dir)
      .iterator()
      .asScala
      .map(p => root.relativize(p))
      .map(_.toString)
      .toSet
  }

  override def content(name: String): Future[Option[Array[Byte]]] = exists(name) map {
    case true =>
      Some(Files.readAllBytes(file(name)))
    case false => None
  }

  override def download(name: String): Future[Option[Source[ByteString, NotUsed]]] = Future {
    if (Files.exists(file(name))) {
      Some(FileIO.fromPath(file(name)).mapMaterializedValue(_ => NotUsed))
    } else {
      None
    }
  }

  override def upload(name: String): Future[Sink[ByteString, Future[Done]]] = Future {
    val f = ensureParents(file(name))
    FileIO.toPath(f).mapMaterializedValue(_.map(_ => Done))
  }

  override def delete(name: String): Future[String] = exists(name).map { e =>
    if (e) {
      Files.delete(file(name))
    }
    name
  }

  override def url(name: String): Future[Option[URL]] = exists(name) map {
    case true =>
      Try(new URL(protocol, resourcePath, name)).toOption
    case false =>
      None
  }
}
