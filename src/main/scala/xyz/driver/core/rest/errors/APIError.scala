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

final case class InvalidActionError(override val message: String = "This action is not allowed",
                                    override val isPatientSensitive: Boolean = false)
    extends APIError {
  override def statusCode: StatusCode = StatusCodes.Forbidden
}

final case class ResourceNotFoundError(override val message: String = "Resource not found",
                                       override val isPatientSensitive: Boolean = false)
    extends APIError {
  override def statusCode: StatusCode = StatusCodes.NotFound
}

final case class ExternalServiceTimeoutError(override val message: String = "Another service took too long to respond",
                                             override val isPatientSensitive: Boolean = false)
    extends APIError {
  override def statusCode: StatusCode = StatusCodes.GatewayTimeout
}

final case class DatabaseError(override val message: String = "Database access error",
                               override val isPatientSensitive: Boolean = false)
    extends APIError {
  override def statusCode: StatusCode = StatusCodes.InternalServerError
}
