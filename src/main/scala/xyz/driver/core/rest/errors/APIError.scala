package xyz.driver.core.rest.errors

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

abstract class APIError extends Throwable {
  def isPatientSensitive: Boolean = false

  def statusCode: StatusCode
  def message: String
}

final case class InvalidInputError(override val message: String = "Invalid input",
                                   override val isPatientSensitive: Boolean = false)
    extends APIError {
  override def statusCode: StatusCode = StatusCodes.BadRequest
}
