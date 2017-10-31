package xyz.driver.core.rest.errors

abstract class ServiceException extends Exception {
  def message: String
}

final case class InvalidInputException(override val message: String = "Invalid input") extends ServiceException

final case class InvalidActionException(override val message: String = "This action is not allowed")
    extends ServiceException

final case class ResourceNotFoundException(override val message: String = "Resource not found")
    extends ServiceException

final case class ExternalServiceException(serviceName: String, serviceMessage: String) extends ServiceException {
  override def message = s"Error while calling another service: $serviceMessage"
}

final case class ExternalServiceTimeoutException(
        override val message: String = "Another service took too long to respond")
    extends ServiceException

final case class DatabaseException(override val message: String = "Database access error") extends ServiceException
