package xyz.driver.core.rest.errors

import akka.http.scaladsl.model.{StatusCode, StatusCodes}

abstract class ServiceException extends Exception {
  def isPatientSensitive: Boolean = false

  def statusCode: StatusCode
  def message: String
}

final case class InvalidInputException(override val message: String = "Invalid input",
                                       override val isPatientSensitive: Boolean = false)
    extends ServiceException {
  override def statusCode: StatusCode = StatusCodes.BadRequest
}

final case class InvalidActionException(override val message: String = "This action is not allowed",
                                        override val isPatientSensitive: Boolean = false)
    extends ServiceException {
  override def statusCode: StatusCode = StatusCodes.Forbidden
}

final case class ResourceNotFoundException(override val message: String = "Resource not found",
                                           override val isPatientSensitive: Boolean = false)
    extends ServiceException {
  override def statusCode: StatusCode = StatusCodes.NotFound
}

final case class ExternalServiceTimeoutException(override val message: String =
                                                   "Another service took too long to respond",
                                                 override val isPatientSensitive: Boolean = false)
    extends ServiceException {
  override def statusCode: StatusCode = StatusCodes.GatewayTimeout
}

final case class DatabaseException(override val message: String = "Database access error",
                                   override val isPatientSensitive: Boolean = false)
    extends ServiceException {
  override def statusCode: StatusCode = StatusCodes.InternalServerError
}
