package xyz.driver.core.rest

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{FlatSpec, Matchers}
import spray.json._
import xyz.driver.core.{Id, Name}
import xyz.driver.core.json._

import scala.concurrent.Future

class PatchSupportTest
    extends FlatSpec with Matchers with ScalatestRouteTest with SprayJsonSupport with DefaultJsonProtocol
    with Directives with PatchSupport {
  case class Bar(name: Name[Bar], size: Int)
  case class Foo(id: Id[Foo], name: Name[Foo], rank: Int, bar: Option[Bar])
  implicit val barFormat: RootJsonFormat[Bar] = jsonFormat2(Bar)
  implicit val fooFormat: RootJsonFormat[Foo] = jsonFormat4(Foo)

  val testFoo: Foo = Foo(Id("1"), Name(s"Foo"), 1, Some(Bar(Name("Bar"), 10)))

  def route(implicit patchRetrievable: PatchRetrievable[Foo]): Route =
    Route.seal(path("api" / "v1" / "foos" / IdInPath[Foo]) { fooId =>
      patch(as[Foo], fooId) { patchedFoo =>
        complete(patchedFoo)
      }
    })

  def jsonEntity(json: String): RequestEntity = HttpEntity(ContentTypes.`application/json`, json)

  "PatchSupport" should "allow partial updates to an existing object" in {
    implicit val fooPatchable: PatchRetrievable[Foo] = id => Future.successful(Some(testFoo.copy(id = id)))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4}""")) ~> route ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(rank = 4)
    }
  }

  it should "merge deeply nested objects" in {
    implicit val fooPatchable: PatchRetrievable[Foo] = id => Future.successful(Some(testFoo.copy(id = id)))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4, "bar": {"name": "My Bar"}}""")) ~> route ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(rank = 4, bar = Some(Bar(Name("My Bar"), 10)))
    }
  }

  it should "return a 404 if the object is not found" in {
    implicit val fooPatchable: PatchRetrievable[Foo] = _ => Future.successful(Option.empty[Foo])

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": 4}""")) ~> route ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "handle nulls on optional values correctly" in {
    implicit val fooPatchable: PatchRetrievable[Foo] = id => Future.successful(Some(testFoo.copy(id = id)))

    Patch("/api/v1/foos/1", jsonEntity("""{"bar": null}""")) ~> route ~> check {
      handled shouldBe true
      responseAs[Foo] shouldBe testFoo.copy(bar = None)
    }
  }

  it should "return a 400 for nulls on non-optional values" in {
    implicit val fooPatchable: PatchRetrievable[Foo] = id => Future.successful(Some(testFoo.copy(id = id)))

    Patch("/api/v1/foos/1", jsonEntity("""{"rank": null}""")) ~> route ~> check {
      handled shouldBe true
      status shouldBe StatusCodes.BadRequest
    }
  }
}
