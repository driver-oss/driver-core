package xyz.driver.core
package rest
import scala.annotation.implicitNotFound

@implicitNotFound(
  "Don't know how to communicate with service ${S}. Make sure an implicit ServiceDescriptor is" +
    "available. A good place to put one is in the service's companion object.")
trait ServiceDescriptor[S] {

  /** The service's name. Must be unique among all services. */
  def name: String

  /** Get an instance of the service. */
  def connect(transport: HttpRestServiceTransport, url: String): S

}
