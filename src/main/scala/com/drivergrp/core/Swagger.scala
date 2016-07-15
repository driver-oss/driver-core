package com.drivergrp.core

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka.model._
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.config.Config

import scala.reflect.runtime.universe._


class Swagger(override val actorSystem: ActorSystem,
              override val apiTypes: Seq[Type],
              val config: Config) extends SwaggerHttpService with HasActorSystem {

  val materializer = ActorMaterializer()(actorSystem)

  override val host = "localhost:8080" //the url of your api, not swagger's json endpoint
  override val basePath = config.getString("swagger.basePath")
  override val apiDocsPath = config.getString("swagger.docsPath")

  override val info = Info(
    config.getString("swagger.apiInfo.description"),
    config.getString("swagger.apiVersion"),
    config.getString("swagger.apiInfo.title"),
    config.getString("swagger.apiInfo.termsOfServiceUrl"),
    contact = Some(Contact(
      config.getString("swagger.apiInfo.contact.name"),
      config.getString("swagger.apiInfo.contact.url"),
      config.getString("swagger.apiInfo.contact.email")
    )),
    license = Some(License(
      config.getString("swagger.apiInfo.license"),
      config.getString("swagger.apiInfo.licenseUrl")
    )),
    vendorExtensions = Map())

  def swaggerUI = get {
    pathPrefix("") {
      pathEndOrSingleSlash {
        getFromResource("swagger-ui/index.html")
      }
    } ~ getFromResourceDirectory("swagger-ui")
  }
}