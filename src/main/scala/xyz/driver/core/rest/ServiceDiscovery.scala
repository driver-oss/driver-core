package xyz.driver.core.rest

import xyz.driver.core.Name

trait ServiceDiscovery {

  def discover[T <: Service](serviceName: Name[Service]): T
}
