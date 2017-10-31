package xyz.driver.core.logging

import java.time.{LocalDateTime, ZoneId}

import org.slf4j.LoggerFactory
import xyz.driver.core.Id
import xyz.driver.core.auth.User
import xyz.driver.core.logging.phi._

trait PhiSafeLogger {

  def error(message: NoPhiString): Unit

  def warn(message: NoPhiString): Unit

  def info(message: NoPhiString): Unit

  def debug(message: NoPhiString): Unit

  def trace(message: NoPhiString): Unit

}

class DefaultPhiSafeLogger(underlying: org.slf4j.Logger) extends PhiSafeLogger {

  def error(message: NoPhiString): Unit = underlying.error(message.text)

  def warn(message: NoPhiString): Unit = underlying.warn(message.text)

  def info(message: NoPhiString): Unit = underlying.info(message.text)

  def debug(message: NoPhiString): Unit = underlying.debug(message.text)

  def trace(message: NoPhiString): Unit = underlying.trace(message.text)

}

trait PhiSafeLogging {

  protected val logger: PhiSafeLogger = new DefaultPhiSafeLogger(LoggerFactory.getLogger(getClass.getName))

  /**
    * Logs the failMessage on an error level, if isSuccessful is false.
    * @return isSuccessful
    */
  protected def loggedError(isSuccessful: Boolean, failMessage: NoPhiString): Boolean = {
    if (!isSuccessful) {
      logger.error(failMessage)
    }
    isSuccessful
  }

  protected def logTime(userId: Id[User], label: NoPhiString, obj: NoPhiString): Unit = {
    val now = LocalDateTime.now(ZoneId.of("Z"))
    logger.info(noPhi"User id=${NoPhi(userId)} performed an action at $label=$now with a $obj")
  }
}
