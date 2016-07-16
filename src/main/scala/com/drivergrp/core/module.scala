package com.drivergrp.core

import akka.http.scaladsl.server.{Route, RouteConcatenation}


object module {
  import scala.reflect.runtime.universe._

  trait Module {
    val name: String
    def route: Route
    def routeTypes: Seq[Type]

    def activate(): Unit = {}
    def deactivate(): Unit = {}
  }

  class SimpleModule(val name: String, val route: Route, routeType: Type) extends Module {
    def routeTypes: Seq[Type] = Seq(routeType)
  }

  /**
    * Module implementation which may be used to composed a few
    *
    * @param name more general name of the composite module,
    *             must be provided as there is no good way to automatically
    *             generalize the name from the composed modules' names
    * @param modules modules to compose into a single one
    */
  class CompositeModule(val name: String, modules: Seq[Module])
    extends Module with RouteConcatenation {

    def route: Route = modules.map(_.route).reduce(_ ~ _)
    def routeTypes = modules.flatMap(_.routeTypes)

    override def activate() = modules.foreach(_.activate())
    override def deactivate() = modules.reverse.foreach(_.deactivate())
  }
}