package xyz.driver.core.rest

import xyz.driver.core.Name

trait ServiceDiscovery {

  def discover[T <: Service](serviceName: Name[Service]): T
}

object ServiceDiscovery {
  trait SavingUsedServiceDiscovery {
    private val usedServices = new scala.collection.mutable.HashSet[String]()

    def saveServiceUsage(serviceName: Name[Service]): Unit = usedServices.synchronized {
      usedServices += serviceName.value
    }

    def getUsedServices: Set[String] = usedServices.synchronized { usedServices.toSet }
  }

  class NoServiceDiscovery extends ServiceDiscovery with SavingUsedServiceDiscovery {

    def discover[T <: Service](serviceName: Name[Service]): T =
      throw new IllegalArgumentException(s"Service with name $serviceName is unknown")
  }
}
