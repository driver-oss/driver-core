package xyz.driver.core.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import spray.json._
import xyz.driver.core.{Id, Name}
import xyz.driver.core.json._

import scala.concurrent.Future

class PatchDirectivesTest
    extends FlatSpec with Matchers with ScalatestRouteTest with SprayJsonSupport with DefaultJsonProtocol
    with Directives with PatchDirectives {
  case class Bar(name: Name[Bar], size: Int)
  case class Foo(id: Id[Foo], name: Name[Foo], rank: Int, bar: Option[Bar])
  implicit val barFormat: RootJsonFormat[Bar] = jsonFormat2(Bar)
  implicit val fooFormat: RootJsonFormat[Foo] = jsonFormat4(Foo)

  val testFoo: Foo = Foo(Id("1"), Name(s"Foo"), 1, Some(Bar(Name("Bar"), 10)))

  def route(retrieve: => Future[Option[Foo]]): Route =
    Route.seal(path("api" / "v1" / "foos" / IdInPath[Foo]) { fooId =>
      entity(as[Patchable[Foo]]) { fooPatchable =>
        mergePatch(fooPatchable, retrieve) { updatedFoo =>
          complete(updatedFoo)
        }
      }
    })

  val MergePatchContentType = ContentType(`application/merge-patch+json`)
  val ContentTypeHeader     = `Content-Type`(MergePatchContentType)
  def jsonEntity(json: String, contentType: ContentType.NonBinary = MergePatchContentType): RequestEntity =
    HttpEntity(contentType, json)

  "PatchSupport" should "allow partial updates to an existing object" in {
    val fooRetrieve = Future.successful(Some(testFoo))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(rank = 4)
    }
  }

  it should "merge deeply nested objects" in {
    val fooRetrieve = Future.successful(Some(testFoo))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4, "bar": {"name": "My Bar"}}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(rank = 4, bar = Some(Bar(Name("My Bar"), 10)))
    }
  }

  it should "return a 404 if the object is not found" in {
    val fooRetrieve = Future.successful(None)

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "handle nulls on optional values correctly" in {
    val fooRetrieve = Future.successful(Some(testFoo))

    Patch("/api/v1/foos/1", jsonEntity("""{"bar": null}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(bar = None)
    }
  }

  it should "handle optional values correctly when old value is null" in {
    val fooRetrieve = Future.successful(Some(testFoo.copy(bar = None)))

    Patch("/api/v1/foos/1", jsonEntity("""{"bar": {"name": "My Bar","size":10}}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(bar = Some(Bar(Name("My Bar"), 10)))
    }
  }

  it should "return a 400 for nulls on non-optional values" in {
    val fooRetrieve = Future.successful(Some(testFoo))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": null}""")) ~> route(fooRetrieve) ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.BadRequest
    }
  }

  it should "return a 415 for incorrect Content-Type" in {
    val fooRetrieve = Future.successful(Some(testFoo))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4}""", ContentTypes.`application/json`)) ~> route(fooRetrieve) ~> check {
      status shouldBe StatusCodes.UnsupportedMediaType
      responseAs[String] should include("application/merge-patch+json")
    }
  }
}
