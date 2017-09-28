package xyz.driver.core.rest

import xyz.driver.core.Name

class NoServiceDiscovery extends ServiceDiscovery with SavingUsedServiceDiscovery {

  def discover[T <: Service](serviceName: Name[Service]): T =
    throw new IllegalArgumentException(s"Service with name $serviceName is unknown")
}
