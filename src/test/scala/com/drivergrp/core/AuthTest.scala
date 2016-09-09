package com.drivergrp.core

import com.drivergrp.core.auth._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import Directives._
import akka.http.scaladsl.model.headers.RawHeader
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class AuthTest extends FlatSpec with Matchers with MockitoSugar with ScalatestRouteTest {

  "'authorize' directive" should "throw error is auth token is not in the request" in {

    Get("/naive/attempt") ~>
    auth.directives.authorize(CanSignOutReport) { authToken =>
      complete("Never going to be here")
    } ~>
    check {
      handled shouldBe false
      rejections should contain(MissingHeaderRejection("WWW-Authenticate"))
    }
  }

  it should "throw error is authorized user is not having the requested permission" in {

    val referenceAuthToken = AuthToken(Base64("I am a pathologist's token"))

    Post("/administration/attempt").addHeader(
        RawHeader(auth.directives.AuthenticationTokenHeader, s"Macaroon ${referenceAuthToken.value.value}")
    ) ~>
    auth.directives.authorize(CanAssignRoles) { authToken =>
      complete("Never going to get here")
    } ~>
    check {
      handled shouldBe false
      rejections should contain(ValidationRejection("User does not have the required permission CanAssignRoles", None))
    }
  }

  it should "pass and retrieve the token to client code, if token is in request and user has permission" in {

    val referenceAuthToken = AuthToken(Base64("I am token"))

    Get("/valid/attempt/?a=2&b=5").addHeader(
        RawHeader(auth.directives.AuthenticationTokenHeader, s"Macaroon ${referenceAuthToken.value.value}")
    ) ~>
    auth.directives.authorize(CanSignOutReport) { authToken =>
      complete("Alright, \"" + authToken.value.value + "\" is handled")
    } ~>
    check {
      handled shouldBe true
      responseAs[String] shouldBe "Alright, \"Macaroon I am token\" is handled"
    }
  }
}
