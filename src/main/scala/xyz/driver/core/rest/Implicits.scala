package xyz.driver.core.rest

import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model.StatusCodes

import scala.concurrent.Future
import scalaz.{Functor, OptionT}

object Implicits {
  implicit class OptionTRestAdditions[T](optionT: OptionT[Future, T]) {
    def responseOrNotFound(successCode: StatusCodes.Success = StatusCodes.OK)(
            implicit F: Functor[Future],
            em: ToEntityMarshaller[T]): Future[ToResponseMarshallable] = {
      optionT.fold[ToResponseMarshallable](successCode -> _, StatusCodes.NotFound -> None)
    }
  }
}
