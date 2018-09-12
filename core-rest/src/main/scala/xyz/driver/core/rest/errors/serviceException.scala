package xyz.driver.core.rest.errors

sealed abstract class ServiceException(val message: String) extends Exception(message)

final case class InvalidInputException(override val message: String = "Invalid input") extends ServiceException(message)

final case class InvalidActionException(override val message: String = "This action is not allowed")
    extends ServiceException(message)

final case class UnauthorizedException(
    override val message: String = "The user's authentication credentials are invalid or missing")
    extends ServiceException(message)

final case class ResourceNotFoundException(override val message: String = "Resource not found")
    extends ServiceException(message)

final case class ExternalServiceException(
    serviceName: String,
    serviceMessage: String,
    serviceException: Option[ServiceException])
    extends ServiceException(s"Error while calling '$serviceName': $serviceMessage")

final case class ExternalServiceTimeoutException(serviceName: String)
    extends ServiceException(s"$serviceName took too long to respond")

final case class DatabaseException(override val message: String = "Database access error")
    extends ServiceException(message)
