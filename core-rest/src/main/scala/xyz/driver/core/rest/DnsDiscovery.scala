package xyz.driver.core
package rest

class DnsDiscovery(transport: HttpRestServiceTransport, overrides: Map[String, String]) {

  def discover[A](implicit descriptor: ServiceDescriptor[A]): A = {
    val url = overrides.getOrElse(descriptor.name, s"https://${descriptor.name}")
    descriptor.connect(transport, url)
  }

}
