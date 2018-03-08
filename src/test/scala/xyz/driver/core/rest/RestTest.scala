package xyz.driver.core.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route, ValidationRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}
import xyz.driver.core.rest

import scala.concurrent.Future
import scala.util.Random

class RestTest extends WordSpec with Matchers with ScalatestRouteTest with Directives {
  "`escapeScriptTags` function" should {
    "escape script tags properly" in {
      val dirtyString = "</sc----</sc----</sc"
      val cleanString = "--------------------"

      (escapeScriptTags(ByteString(dirtyString)).utf8String) should be(dirtyString.replace("</sc", "< /sc"))

      (escapeScriptTags(ByteString(cleanString)).utf8String) should be(cleanString)
    }
  }

  "paginated directive" should {
    val route: Route = rest.paginated { paginated =>
      complete(StatusCodes.OK -> s"${paginated.pageNumber},${paginated.pageSize}")
    }
    "accept a pagination" in {
      Get("/?pageNumber=2&pageSize=42") ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == "2,42")
      }
    }
    "provide a default pagination" in {
      Get("/") ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == "1,100")
      }
    }
    "provide default values for a partial pagination" in {
      Get("/?pageSize=2") ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == "1,2")
      }
    }
    "reject an invalid pagination" in {
      Get("/?pageNumber=-1") ~> route ~> check {
        assert(rejection.isInstanceOf[ValidationRejection])
      }
    }
  }

  "optional paginated directive" should {
    val route: Route = rest.optionalPagination { paginated =>
      complete(StatusCodes.OK -> paginated.map(p => s"${p.pageNumber},${p.pageSize}").getOrElse("no pagination"))
    }
    "accept a pagination" in {
      Get("/?pageNumber=2&pageSize=42") ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == "2,42")
      }
    }
    "without pagination" in {
      Get("/") ~> route ~> check {
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == "no pagination")
      }
    }
    "reject an invalid pagination" in {
      Get("/?pageNumber=1") ~> route ~> check {
        assert(rejection.isInstanceOf[ValidationRejection])
      }
    }
  }

  "completeWithPagination directive" when {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    import spray.json.DefaultJsonProtocol._

    val data = Seq.fill(103)(Random.alphanumeric.take(10).mkString)
    val route: Route =
      parameter('empty.as[Boolean] ? false) { isEmpty =>
        completeWithPagination[String] {
          case Some(pagination) if isEmpty =>
            Future.successful(ListResponse(Seq(), 0, Some(pagination)))
          case Some(pagination) =>
            val filtered = data.slice(pagination.offset, pagination.offset + pagination.pageSize)
            Future.successful(ListResponse(filtered, data.size, Some(pagination)))
          case None if isEmpty => Future.successful(ListResponse(Seq(), 0, None))
          case None            => Future.successful(ListResponse(data, data.size, None))
        }
      }

    "pagination is defined" should {
      "return a response with pagination headers" in {
        Get("/?pageNumber=2&pageSize=10") ~> route ~> check {
          responseAs[Seq[String]] shouldBe data.slice(10, 20)
          header(ContextHeaders.ResourceCount).map(_.value) should contain("103")
          header(ContextHeaders.PageCount).map(_.value) should contain("11")
        }
      }

      "disallow pageSize <= 0" in {
        Get("/?pageNumber=2&pageSize=0") ~> route ~> check {
          rejection shouldBe a[ValidationRejection]
        }

        Get("/?pageNumber=2&pageSize=-1") ~> route ~> check {
          rejection shouldBe a[ValidationRejection]
        }
      }

      "disallow pageNumber <= 0" in {
        Get("/?pageNumber=0&pageSize=10") ~> route ~> check {
          rejection shouldBe a[ValidationRejection]
        }

        Get("/?pageNumber=-1&pageSize=10") ~> route ~> check {
          rejection shouldBe a[ValidationRejection]
        }
      }

      "return PageCount == 0 if returning an empty list" in {
        Get("/?empty=true&pageNumber=2&pageSize=10") ~> route ~> check {
          responseAs[Seq[String]] shouldBe empty
          header(ContextHeaders.ResourceCount).map(_.value) should contain("0")
          header(ContextHeaders.PageCount).map(_.value) should contain("0")
        }
      }
    }

    "pagination is not defined" should {
      "return a response with pagination headers and PageCount == 1" in {
        Get("/") ~> route ~> check {
          responseAs[Seq[String]] shouldBe data
          header(ContextHeaders.ResourceCount).map(_.value) should contain("103")
          header(ContextHeaders.PageCount).map(_.value) should contain("1")
        }
      }

      "return PageCount == 0 if returning an empty list" in {
        Get("/?empty=true") ~> route ~> check {
          responseAs[Seq[String]] shouldBe empty
          header(ContextHeaders.ResourceCount).map(_.value) should contain("0")
          header(ContextHeaders.PageCount).map(_.value) should contain("0")
        }
      }
    }
  }
}
