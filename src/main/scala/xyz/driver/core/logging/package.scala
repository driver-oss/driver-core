package xyz.driver.core

import com.typesafe.scalalogging.Logger
import org.slf4j.helpers.NOPLogger

package object logging {
  val NoLogger: Logger = Logger.apply(NOPLogger.NOP_LOGGER)
}
