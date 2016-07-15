package com.drivergrp.core

import akka.http.scaladsl.server.{Route, RouteConcatenation}


trait Service {
  import scala.reflect.runtime.universe._

  val name: String
  def route: Route
  def serviceTypes: Seq[Type]

  def activate(): Unit = {}
  def deactivate(): Unit = {}
}

/**
  * Service implementation which may be used to composed a few
  *
  * @param name more general name of the composite service,
  *             must be provided as there is no good way to automatically
  *             generalize the name from the composed services' names
  * @param services services to compose into a single one
  */
class CompositeService(val name: String, services: Seq[Service])
  extends Service with RouteConcatenation {

  def route: Route = services.map(_.route).reduce(_ ~ _)

  def serviceTypes = services.flatMap(_.serviceTypes)

  override def activate() = services.foreach(_.activate())
  override def deactivate() = services.reverse.foreach(_.deactivate())
}