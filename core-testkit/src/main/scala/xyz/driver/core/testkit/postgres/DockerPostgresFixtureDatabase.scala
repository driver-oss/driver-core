package xyz.driver.core.testkit
package postgres

import java.net.ServerSocket

import org.scalatest.{BeforeAndAfterAll, Suite}
import slick.jdbc.PostgresProfile
import xyz.driver.core.using

import scala.concurrent.Await
import scala.concurrent.duration._

trait DockerPostgresFixtureDatabase
    extends FixtureDatabase[PostgresProfile] with DockerPostgresDatabase with BeforeAndAfterAll {
  self: Suite =>
  private val dbName: String   = sys.env.getOrElse("TEST_DB_NAME", "postgres")
  private val username: String = sys.env.getOrElse("TEST_DB_USERNAME", "postgres")
  private val password: String = sys.env.getOrElse("TEST_DB_PASSWORD", "postgres")
  private val port: Int        = sys.env.get("TEST_DB_PORT").fold(getRandomPort())(_.toInt)

  private val enabled: Boolean = !sys.env.get("DISABLE_DOCKER_TEST_DB").contains("true")

  protected val connectionTimeout: Duration = 16.seconds

  final val profile = PostgresProfile
  final override val database = profile.backend.Database.forURL(
    driver = "org.postgresql.Driver",
    url = s"jdbc:postgresql://localhost:$port/$dbName",
    user = username,
    password = password)

  object driverDatabase extends xyz.driver.core.database.Database {
    override val profile  = self.profile
    override val database = self.database
  }

  private def getRandomPort(): Int = using(new ServerSocket(0))(_.getLocalPort())

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private var dockerContainerId: Option[String] = None

  private def waitForContainer(): Unit = {
    import concurrent.ExecutionContext.Implicits.global
    import profile.api._

    val expiration = System.currentTimeMillis() + connectionTimeout.toMillis

    val query = sql"SELECT 1;".as[Int]
    def dbReady = Await.result(
      database
        .run(query)
        .map(_ == Vector(1))
        .recover({ case _: org.postgresql.util.PSQLException => false }),
      connectionTimeout
    )

    while (!dbReady && System.currentTimeMillis() < expiration) {
      Thread.sleep(100)
    }
  }

  override protected def beforeAll(): Unit = {
    if (enabled) {
      dockerContainerId = Some(super.setupDockerDatabase(username, password, dbName, port))
      waitForContainer()
    }
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    try super.afterAll()
    finally dockerContainerId.foreach(killDockerDatabase)
  }
}
