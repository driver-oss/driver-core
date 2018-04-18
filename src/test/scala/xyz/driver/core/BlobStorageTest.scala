package xyz.driver.core

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import xyz.driver.core.storage.{BlobStorage, FileSystemBlobStorage}

import scala.concurrent.Future
import scala.concurrent.duration._

class BlobStorageTest extends FlatSpec with ScalaFutures {

  implicit val patientce = PatienceConfig(timeout = 100.seconds)

  implicit val system = ActorSystem("blobstorage-test")
  implicit val mat    = ActorMaterializer()
  import system.dispatcher

  def storageBehaviour(storage: BlobStorage) = {
    val key  = "foo"
    val data = "hello world".getBytes
    it should "upload data" in {
      assert(storage.exists(key).futureValue === false)
      assert(storage.uploadContent(key, data).futureValue === key)
      assert(storage.exists(key).futureValue === true)
    }
    it should "download data" in {
      val content = storage.content(key).futureValue
      assert(content.isDefined)
      assert(content.get === data)
    }
    it should "not download non-existing data" in {
      assert(storage.content("bar").futureValue.isEmpty)
    }
    it should "overwrite an existing key" in {
      val newData = "new string".getBytes("utf-8")
      assert(storage.uploadContent(key, newData).futureValue === key)
      assert(storage.content(key).futureValue.get === newData)
    }
    it should "upload a file" in {
      val tmp = Files.createTempFile("testfile", "txt")
      Files.write(tmp, data)
      assert(storage.uploadFile(key, tmp).futureValue === key)
      Files.delete(tmp)
    }
    it should "upload content" in {
      val text = "foobar"
      val src = Source
        .single(text)
        .map(l => ByteString(l))
      src.runWith(storage.upload(key).futureValue).futureValue
      assert(storage.content(key).futureValue.map(_.toSeq) === Some("foobar".getBytes.toSeq))
    }
    it should "delete content" in {
      assert(storage.exists(key).futureValue)
      storage.delete(key).futureValue
      assert(!storage.exists(key).futureValue)
    }
    it should "download content" in {
      storage.uploadContent(key, data) futureValue
      val srcOpt = storage.download(key).futureValue
      assert(srcOpt.isDefined)
      val src                          = srcOpt.get
      val content: Future[Array[Byte]] = src.runWith(Sink.fold(Array[Byte]())(_ ++ _))
      assert(content.futureValue === data)
    }
    it should "list keys" in {
      assert(storage.list("").futureValue === Set(key))
      storage.uploadContent("a/a.txt", data).futureValue
      storage.uploadContent("a/b", data).futureValue
      storage.uploadContent("c/d", data).futureValue
      storage.uploadContent("d", data).futureValue
      assert(storage.list("").futureValue === Set(key, "a", "c", "d"))
      assert(storage.list("a").futureValue === Set("a/a.txt", "a/b"))
      assert(storage.list("a").futureValue === Set("a/a.txt", "a/b"))
      assert(storage.list("c").futureValue === Set("c/d"))
    }
    it should "get valid URL" in {
      assert(storage.exists(key).futureValue === true)
      val fooUrl = storage.url(key).futureValue
      assert(fooUrl.isDefined)
    }
  }

  "File system storage" should behave like storageBehaviour(
    new FileSystemBlobStorage(Files.createTempDirectory("test")))

}
