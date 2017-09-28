package xyz.driver.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`User-Agent`
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult, ThrottleMode}
import xyz.driver.core.Name

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class PooledHttpClient(
        baseUri: Uri,
        applicationName: Name[App],
        applicationVersion: String,
        requestRateLimit: Int = 64,
        requestQueueSize: Int = 1024)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext)
    extends HttpClient {

  private val host   = baseUri.authority.host.toString()
  private val port   = baseUri.effectivePort
  private val scheme = baseUri.scheme

  protected implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  private val clientConnectionSettings: ClientConnectionSettings =
    ClientConnectionSettings(actorSystem).withUserAgentHeader(
      Option(`User-Agent`(applicationName.value + "/" + applicationVersion)))

  private val connectionPoolSettings: ConnectionPoolSettings = ConnectionPoolSettings(actorSystem)
    .withConnectionSettings(clientConnectionSettings)

  private val pool = if (scheme.equalsIgnoreCase("https")) {
    Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](host, port, settings = connectionPoolSettings)
  } else {
    Http().cachedHostConnectionPool[Promise[HttpResponse]](host, port, settings = connectionPoolSettings)
  }

  private val queue = Source
    .queue[(HttpRequest, Promise[HttpResponse])](requestQueueSize, OverflowStrategy.dropNew)
    .via(pool)
    .throttle(requestRateLimit, 1.second, maximumBurst = requestRateLimit, ThrottleMode.shaping)
    .toMat(Sink.foreach({
      case ((Success(resp), p)) => p.success(resp)
      case ((Failure(e), p))    => p.failure(e)
    }))(Keep.left)
    .run

  def makeRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()

    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued =>
        responsePromise.future
      case QueueOfferResult.Dropped =>
        Future.failed(new Exception(s"Request queue to the host $host is overflown"))
      case QueueOfferResult.Failure(ex) =>
        Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new Exception("Queue was closed (pool shut down) while running the request"))
    }
  }
}
