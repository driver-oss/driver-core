package xyz.driver.core.rest

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route, ValidationRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}
import xyz.driver.core.rest

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
    val route: Route = rest.optionalPaginated { paginated =>
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
}
