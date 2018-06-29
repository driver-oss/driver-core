package xyz.driver.core

import org.slf4j.helpers.NOPLogger

package object logging {
  val NoLogger = com.typesafe.scalalogging.Logger(NOPLogger.NOP_LOGGER)
}
