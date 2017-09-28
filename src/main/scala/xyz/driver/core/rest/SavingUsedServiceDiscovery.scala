package xyz.driver.core.rest

import xyz.driver.core.Name

trait SavingUsedServiceDiscovery {

  private val usedServices = new scala.collection.mutable.HashSet[String]()

  def saveServiceUsage(serviceName: Name[Service]): Unit = usedServices.synchronized {
    usedServices += serviceName.value
  }

  def getUsedServices: Set[String] = usedServices.synchronized { usedServices.toSet }
}
