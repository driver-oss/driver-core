package xyz.driver.core.testkit

import com.typesafe.scalalogging.Logger
import org.scalatest.{Matchers, Suite}
import org.slf4j.helpers.NOPLogger
import xyz.driver.core.time.Time
import xyz.driver.core.time.provider.{SpecificTimeProvider, TimeProvider}

trait DriverFunctionalTest extends Matchers with postgres.DockerPostgresFixtureDatabase {
  self: Suite =>

  // Needed to reset some external libraries timezones (e.g., HSQLDB)
  // for local development matching CI and deployed environment
  java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Etc/UTC"))

  val log: Logger                = Logger(NOPLogger.NOP_LOGGER)
  val timeProvider: TimeProvider = new SpecificTimeProvider(Time(100000))
}
