package xyz.driver.core

import java.io.{File, FileInputStream}
import java.nio.file.Paths

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.file.{FileSystemStorage, GcsStorage, S3Storage}

import scala.concurrent.Await
import scala.concurrent.duration._

class FileTest extends FlatSpec with Matchers with MockitoSugar {

  "S3 Storage" should "create and download local files and do other operations" in {
    import com.amazonaws.services.s3.AmazonS3
    import com.amazonaws.services.s3.model._
    import scala.collection.JavaConverters._

    val tempDir        = System.getProperty("java.io.tmpdir")
    val sourceTestFile = generateTestLocalFile(tempDir)
    val testFileName   = "uploadTestFile"

    val randomFolderName = java.util.UUID.randomUUID().toString
    val testDirPath      = Paths.get(randomFolderName)
    val testFilePath     = Paths.get(randomFolderName, testFileName)

    val testBucket = Name[Bucket]("IamBucket")

    val s3PutMock = mock[PutObjectResult]
    when(s3PutMock.getETag).thenReturn("IAmEtag")

    val s3ObjectSummaryMock = mock[S3ObjectSummary]
    when(s3ObjectSummaryMock.getKey).thenReturn(testFileName)
    when(s3ObjectSummaryMock.getETag).thenReturn("IAmEtag")
    when(s3ObjectSummaryMock.getLastModified).thenReturn(new java.util.Date())

    val s3ResultsMock = mock[ListObjectsV2Result]
    when(s3ResultsMock.getNextContinuationToken).thenReturn("continuationToken")
    when(s3ResultsMock.isTruncated).thenReturn(false, // before file created it is empty (zero pages)
                                               true,
                                               false, // after file is uploaded it contains this one file (one page)
                                               false) // after file is deleted it is empty (zero pages) again
    when(s3ResultsMock.getObjectSummaries).thenReturn(
      // before file created it is empty, `getObjectSummaries` is never called
      List[S3ObjectSummary](s3ObjectSummaryMock).asJava, // after file is uploaded it contains this one file
      List.empty[S3ObjectSummary].asJava
    ) // after file is deleted it is empty again

    val s3ObjectMetadataMock = mock[ObjectMetadata]
    val amazonS3Mock         = mock[AmazonS3]
    when(amazonS3Mock.listObjectsV2(any[ListObjectsV2Request]())).thenReturn(s3ResultsMock)
    when(amazonS3Mock.putObject(testBucket.value, testFilePath.toString, sourceTestFile)).thenReturn(s3PutMock)
    when(amazonS3Mock.getObject(any[GetObjectRequest](), any[File]())).thenReturn(s3ObjectMetadataMock)

    val s3Storage = new S3Storage(amazonS3Mock, testBucket, scala.concurrent.ExecutionContext.global)

    val filesBefore = Await.result(s3Storage.list(testDirPath).run, 10 seconds)
    filesBefore shouldBe empty

    Await.result(s3Storage.upload(sourceTestFile, testFilePath), 10 seconds)

    val filesAfterUpload = Await.result(s3Storage.list(testDirPath).run, 10 seconds)
    filesAfterUpload.size should be(1)
    val uploadedFileLine = filesAfterUpload.head
    uploadedFileLine.name should be(Name[File](testFileName))
    uploadedFileLine.location should be(testFilePath)
    uploadedFileLine.revision.id.length should be > 0
    uploadedFileLine.lastModificationDate.millis should be > 0L

    val downloadedFile = Await.result(s3Storage.download(testFilePath).run, 10 seconds)
    downloadedFile shouldBe defined
    downloadedFile.foreach {
      _.getAbsolutePath.endsWith(testFilePath.toString) should be(true)
    }

    Await.result(s3Storage.delete(testFilePath), 10 seconds)

    val filesAfterRemoval = Await.result(s3Storage.list(testDirPath).run, 10 seconds)
    filesAfterRemoval shouldBe empty
  }

  "Filesystem files storage" should "create and download local files and do other operations" in {

    val tempDir        = System.getProperty("java.io.tmpdir")
    val sourceTestFile = generateTestLocalFile(tempDir)

    val randomFolderName = java.util.UUID.randomUUID().toString
    val testDirPath      = Paths.get(tempDir, randomFolderName)
    val testFilePath     = Paths.get(tempDir, randomFolderName, "uploadTestFile")

    val fileStorage = new FileSystemStorage(scala.concurrent.ExecutionContext.global)

    val filesBefore = Await.result(fileStorage.list(testDirPath).run, 10 seconds)
    filesBefore shouldBe empty

    Await.result(fileStorage.upload(sourceTestFile, testFilePath), 10 seconds)

    val filesAfterUpload = Await.result(fileStorage.list(testDirPath).run, 10 seconds)
    filesAfterUpload.size should be(1)
    val uploadedFileLine = filesAfterUpload.head
    uploadedFileLine.name should be(Name[File]("uploadTestFile"))
    uploadedFileLine.location should be(testFilePath)
    uploadedFileLine.revision.id.length should be > 0
    uploadedFileLine.lastModificationDate.millis should be > 0L

    val downloadedFile = Await.result(fileStorage.download(testFilePath).run, 10 seconds)
    downloadedFile shouldBe defined
    downloadedFile.map(_.getAbsolutePath) should be(Some(testFilePath.toString))

    Await.result(fileStorage.delete(testFilePath), 10 seconds)

    val filesAfterRemoval = Await.result(fileStorage.list(testDirPath).run, 10 seconds)
    filesAfterRemoval shouldBe empty
  }

  "Google Cloud Storage" should "upload and download files" in {
    import com.google.cloud.Page
    import com.google.cloud.storage.{Blob, Bucket, Storage}
    import Bucket.BlobWriteOption
    import Storage.BlobListOption
    import scala.collection.JavaConverters._

    val tempDir        = System.getProperty("java.io.tmpdir")
    val sourceTestFile = generateTestLocalFile(tempDir)
    val testFileName   = "uploadTestFile"

    val randomFolderName = java.util.UUID.randomUUID().toString
    val testDirPath      = Paths.get(randomFolderName)
    val testFilePath     = Paths.get(randomFolderName, testFileName)

    val testBucket = Name[Bucket]("IamBucket")
    val gcsMock    = mock[Storage]
    val pageMock   = mock[Page[Blob]]
    val bucketMock = mock[Bucket]
    val blobMock   = mock[Blob]

    when(blobMock.getName).thenReturn(testFileName)
    when(blobMock.getGeneration).thenReturn(1000L)
    when(blobMock.getUpdateTime).thenReturn(1493422254L)
    when(blobMock.getSize).thenReturn(12345L)
    when(blobMock.getContent()).thenReturn(Array[Byte](1, 2, 3))

    val gcsStorage = new GcsStorage(gcsMock, testBucket, scala.concurrent.ExecutionContext.global)

    when(pageMock.iterateAll()).thenReturn(
      Iterator[Blob]().asJava,
      Iterator[Blob](blobMock).asJava,
      Iterator[Blob]().asJava
    )
    when(gcsMock.list(testBucket.value, BlobListOption.currentDirectory(), BlobListOption.prefix(s"$testDirPath/")))
      .thenReturn(pageMock)

    val filesBefore = Await.result(gcsStorage.list(testDirPath).run, 10 seconds)
    filesBefore shouldBe empty

    when(gcsMock.get(testBucket.value)).thenReturn(bucketMock)
    when(gcsMock.get(testBucket.value, testFilePath.toString)).thenReturn(blobMock)
    when(bucketMock.create(org.mockito.Matchers.eq(testFileName), any[FileInputStream], any[BlobWriteOption]))
      .thenReturn(blobMock)

    Await.result(gcsStorage.upload(sourceTestFile, testFilePath), 10 seconds)

    val filesAfterUpload = Await.result(gcsStorage.list(testDirPath).run, 10 seconds)
    filesAfterUpload.size should be(1)

    val downloadedFile = Await.result(gcsStorage.download(testFilePath).run, 10 seconds)
    downloadedFile shouldBe defined
    downloadedFile.foreach {
      _.getAbsolutePath should endWith(testFilePath.toString)
    }

    Await.result(gcsStorage.delete(testFilePath), 10 seconds)

    val filesAfterRemoval = Await.result(gcsStorage.list(testDirPath).run, 10 seconds)
    filesAfterRemoval shouldBe empty
  }

  private def generateTestLocalFile(path: String): File = {
    val randomSourceFolderName = java.util.UUID.randomUUID().toString
    val sourceTestFile         = new File(Paths.get(path, randomSourceFolderName, "uploadTestFile").toString)
    sourceTestFile.getParentFile.mkdirs() should be(true)
    sourceTestFile.createNewFile() should be(true)
    using(new java.io.PrintWriter(sourceTestFile)) { _.append("Test File Contents") }
    sourceTestFile
  }
}
