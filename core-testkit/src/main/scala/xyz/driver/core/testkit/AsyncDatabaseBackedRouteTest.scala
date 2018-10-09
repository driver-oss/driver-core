package xyz.driver.core.testkit

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.server.directives.FutureDirectives
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.AsyncFlatSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait AsyncDatabaseBackedRouteTest
    extends AsyncFlatSpec with DriverFunctionalTest with AsyncMockFactory with ScalatestRouteTest {

  def route: FutureDirectives

  val defaultTimeOut: FiniteDuration                                   = 5.seconds
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(defaultTimeOut)

  override implicit val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
  override implicit def executionContext: ExecutionContext = executor

  override def beforeAll: Unit = super.beforeAll()
  override def afterAll: Unit  = super.afterAll()
}
