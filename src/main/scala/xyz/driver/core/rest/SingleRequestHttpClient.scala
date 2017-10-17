package xyz.driver.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.stream.ActorMaterializer
import xyz.driver.core.Name

import scala.concurrent.Future

class SingleRequestHttpClient(applicationName: Name[App], applicationVersion: String, actorSystem: ActorSystem)
    extends HttpClient {

  protected implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
  private val client                                     = Http()(actorSystem)

  private val clientConnectionSettings: ClientConnectionSettings =
    ClientConnectionSettings(actorSystem).withUserAgentHeader(
      Option(`User-Agent`(applicationName.value + "/" + applicationVersion)))

  private val connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings(actorSystem)
    .withConnectionSettings(clientConnectionSettings)

  def makeRequest(request: HttpRequest): Future[HttpResponse] = {
    client.singleRequest(request, settings = connectionPoolSettings)(materializer)
  }
}
