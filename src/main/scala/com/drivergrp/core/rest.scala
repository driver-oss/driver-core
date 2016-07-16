package com.drivergrp.core

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.{Directive, _}
import akka.http.scaladsl.util.FastFuture._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.drivergrp.core.id.{Id, Name}
import com.drivergrp.core.logging.Logger
import com.drivergrp.core.stats.Stats
import com.drivergrp.core.time.TimeRange
import com.drivergrp.core.time.provider.TimeProvider
import spray.json.{DeserializationException, JsNumber, JsString, JsValue, RootJsonFormat}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scalaz.{Failure => _, Success => _, _}


object rest {

  trait RestService {
    def sendRequest(request: HttpRequest): Future[HttpResponse]
  }

  class AkkaHttpRestService(actorSystem: ActorSystem) extends RestService {
    protected implicit val materializer = ActorMaterializer()(actorSystem)

    def sendRequest(request: HttpRequest): Future[HttpResponse] =
      Http()(actorSystem).singleRequest(request)(materializer)
  }

  class ProxyRestService(actorSystem: ActorSystem, log: Logger, stats: Stats,
                         time: TimeProvider, executionContext: ExecutionContext)
    extends AkkaHttpRestService(actorSystem) {

    protected implicit val timeout = Timeout(5 seconds)

    override def sendRequest(request: HttpRequest): Future[HttpResponse] = {

      log.audit(s"Sending to ${request.uri} request $request")

      val requestTime = time.currentTime()
      val response = super.sendRequest(request)

      response.onComplete {
        case Success(_) =>
          val responseTime = time.currentTime()
          log.audit(s"Response from ${request.uri} to request $request is successful")
          stats.recordStats(Seq("request", request.uri.toString, "success"), TimeRange(requestTime, responseTime), 1)

        case Failure(t) =>
          val responseTime = time.currentTime()
          log.audit(s"Failed to receive response from ${request.uri} to request $request")
          log.error(s"Failed to receive response from ${request.uri} to request $request", t)
          stats.recordStats(Seq("request", request.uri.toString, "fail"), TimeRange(requestTime, responseTime), 1)
      } (executionContext)

      response
    }
  }

  object basicFormats {

    implicit def idFormat[T] = new RootJsonFormat[Id[T]] {
      def write(id: Id[T]) = JsNumber(id)

      def read(value: JsValue) = value match {
        case JsNumber(id) => Id[T](id.toLong)
        case _ => throw new DeserializationException("Id expects number")
      }
    }

    implicit def nameFormat[T] = new RootJsonFormat[Name[T]] {
      def write(name: Name[T]) = JsString(name)

      def read(value: JsValue): Name[T] = value match {
        case JsString(name) => Name[T](name)
        case _ => throw new DeserializationException("Name expects string")
      }
    }
  }

  trait OptionTDirectives {

    /**
      * "Unwraps" a `OptionT[Future, T]` and runs the inner route after future
      * completion with the future's value as an extraction of type `Try[T]`.
      */
    def onComplete[T](optionT: OptionT[Future, T]): Directive1[Try[Option[T]]] =
      Directive { inner ⇒ ctx ⇒
        import ctx.executionContext
        optionT.run.fast.transformWith(t ⇒ inner(Tuple1(t))(ctx))
      }
  }
}
