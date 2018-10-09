package xyz.driver.core.testkit

import java.nio.file.{Files, Path}

import scala.concurrent.Await
import scala.concurrent.duration._

import org.scalatest.{BeforeAndAfterEach, Suite}
import slick.basic.BasicProfile
import slick.jdbc.JdbcProfile
import slick.relational.RelationalProfile

trait FixtureDatabase[P <: BasicProfile] { self: Suite =>
  val profile: P
  val database: P#Backend#DatabaseDef
}

trait CreateAndDropSchemaForEach[P <: RelationalProfile] extends BeforeAndAfterEach {
  self: Suite with FixtureDatabase[P] =>
  import profile.api._
  def schema: profile.SchemaDescription

  override protected def beforeEach() = {
    Await.result(database.run(schema.create), 5.seconds)
    super.beforeEach()
  }

  override protected def afterEach() = {
    try super.afterEach()
    finally Await.result(database.run(schema.drop), 5.seconds)
  }
}

trait TruncateSchemaAfterEach[P <: RelationalProfile] extends BeforeAndAfterEach {
  self: Suite with FixtureDatabase[P] =>
  import profile.api._

  def schema: profile.SchemaDescription

  override protected def afterEach() = {
    try super.afterEach()
    finally Await.result(database.run(schema.truncate), 5.seconds)
  }
}

trait InsertBeforeEach[P <: JdbcProfile] extends BeforeAndAfterEach { self: Suite with FixtureDatabase[P] =>
  import profile.api._

  val insertsFiles: Set[Path]

  def insertTestData(insertsFile: Path): DBIO[Int] = {
    val rawInserts = new String(Files.readAllBytes(insertsFile), "UTF-8")
    sql"#$rawInserts".asUpdate
  }

  override protected def beforeEach(): Unit = {
    concurrent.Await.result(database.run(DBIO.sequence(insertsFiles.toList.map(insertTestData))), 30.seconds)
    super.beforeEach()
  }
}
