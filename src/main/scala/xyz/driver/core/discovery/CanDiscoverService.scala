package xyz.driver.core
package discovery

import scala.annotation.implicitNotFound

@implicitNotFound(
  "Don't know how to communicate with service ${Service}. Make sure an implicit CanDiscoverService is" +
    "available. A good place to put one is in the service's companion object.")
trait CanDiscoverService[Service] {
  def discover(platform: Platform): Service
}
