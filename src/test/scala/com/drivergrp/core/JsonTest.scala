package com.drivergrp.core

import com.drivergrp.core.time.provider.SystemTimeProvider
import org.scalatest.{FlatSpec, Matchers}

class JsonTest extends FlatSpec with Matchers {

  "Json format for Id" should "read and write correct JSON" in {

    val referenceId = Id[String](1312L)

    val writtenJson = com.drivergrp.core.json.basicFormats.idFormat.write(referenceId)
    writtenJson.prettyPrint should be("1312")

    val parsedId = com.drivergrp.core.json.basicFormats.idFormat.read(writtenJson)
    parsedId should be(referenceId)
  }

  "Json format for Name" should "read and write correct JSON" in {

    val referenceName = Name[String]("Homer")

    val writtenJson = com.drivergrp.core.json.basicFormats.nameFormat.write(referenceName)
    writtenJson.prettyPrint should be("\"Homer\"")

    val parsedName = com.drivergrp.core.json.basicFormats.nameFormat.read(writtenJson)
    parsedName should be(referenceName)
  }

  "Json format for Time" should "read and write correct JSON" in {

    val referenceTime = new SystemTimeProvider().currentTime()

    val writtenJson = com.drivergrp.core.json.basicFormats.timeFormat.write(referenceTime)
    writtenJson.prettyPrint should be("{\n  \"timestamp\": " + referenceTime.millis + "\n}")

    val parsedTime = com.drivergrp.core.json.basicFormats.timeFormat.read(writtenJson)
    parsedTime should be(referenceTime)
  }
}
