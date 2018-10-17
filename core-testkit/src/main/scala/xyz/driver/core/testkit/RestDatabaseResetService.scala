package xyz.driver.core.testkit

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import spray.json._
import xyz.driver.core.Name
import xyz.driver.core.rest.{RestService, ServiceRequestContext, ServiceTransport}

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, OptionT}

object RestDatabaseResetService {
  import DefaultJsonProtocol._
  import xyz.driver.core.json._

  final case class Application(name: Name[Application], tag: String)

  final case class Environment(name: Name[Environment], applications: Seq[Application], status: String)

  implicit val applicationJsonFormat = jsonFormat2(Application)

  implicit val environmentJsonFormat = jsonFormat3(Environment)
}

class RestDatabaseResetService(
    transport: ServiceTransport,
    baseUri: Uri,
    actorSystem: ActorSystem,
    executionContext: ExecutionContext)
    extends RestService {

  import DefaultJsonProtocol._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import RestDatabaseResetService._

  protected implicit val exec         = executionContext
  protected implicit val materializer = ActorMaterializer()(actorSystem)

  def getAvailableEnvironments(): ListT[Future, String] = {
    val request = get(baseUri, s"/v1/environment")
    listResponse[String](transport.sendRequest(new ServiceRequestContext())(request))
  }

  def getEnvironmentDetails(envName: Name[Environment]): ListT[Future, Environment] = {
    val requestStub = get(baseUri, s"/v1/environment/$envName")
    listResponse[Environment](transport.sendRequest(new ServiceRequestContext())(requestStub))
  }

  def getEnvironmentStatus(envName: Name[Environment]): OptionT[Future, String] = {
    val requestStub = get(baseUri, s"/v1/environment/$envName/status")
    optionalResponse[String](transport.sendRequest(new ServiceRequestContext())(requestStub))
  }

  def resetEnvironmentStatus(envName: Name[Environment], newStatus: String): OptionT[Future, String] = {
    val requestStub = HttpRequest(HttpMethods.POST, endpointUri(baseUri, s"/v1/environment/$envName/status"))
    optionalResponse[String](transport.sendRequest(new ServiceRequestContext())(requestStub))
  }

  def resetEnvironmentApplicationsData(envName: Name[Environment]): OptionT[Future, Unit] = {
    val requestStub = HttpRequest(HttpMethods.POST, endpointUri(baseUri, s"/v1/environment/$envName/reset"))
    unitResponse(transport.sendRequest(new ServiceRequestContext())(requestStub))
  }

  def updateEnvironmentState(updatedEnvironment: Environment)(
      implicit ctx: ServiceRequestContext): OptionT[Future, Environment] = {
    val requestStub = postJson(baseUri, s"/v1/environment/${updatedEnvironment.name}", updatedEnvironment.toJson)
    optionalResponse[Environment](transport.sendRequest(ctx)(requestStub))
  }
}
