package xyz.driver

import xyz.driver.core.testkit
import slick.basic.BasicProfile
import slick.jdbc.JdbcProfile
import slick.relational.RelationalProfile

package object test {

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type AsyncDatabaseBackedRouteTest = testkit.AsyncDatabaseBackedRouteTest

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type DriverFunctionalTest = testkit.DriverFunctionalTest

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type FixtureDatabase[P <: BasicProfile] = testkit.FixtureDatabase[P]

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type CreateAndDropSchemaForEach[P <: RelationalProfile] = testkit.CreateAndDropSchemaForEach[P]

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type TruncateSchemaAfterEach[P <: RelationalProfile] = testkit.TruncateSchemaAfterEach[P]

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type InsertBeforeEach[P <: JdbcProfile] = testkit.InsertBeforeEach[P]

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  type RestDatabaseResetService = testkit.RestDatabaseResetService

  @deprecated("moved to package `xyz.driver.core.testkit`", "2.0")
  val RestDatabaseResetService: testkit.RestDatabaseResetService.type = testkit.RestDatabaseResetService

}

package test {
  package object postgres {

    @deprecated("moved to package `xyz.driver.core.testkit.postgres`", "2.0")
    type DockerPostgresDatabase = xyz.driver.core.testkit.postgres.DockerPostgresDatabase

    @deprecated("moved to package `xyz.driver.core.testkit.postgres`", "2.0")
    type DockerPostgresFixtureDatabase = xyz.driver.core.testkit.postgres.DockerPostgresFixtureDatabase

  }
}
