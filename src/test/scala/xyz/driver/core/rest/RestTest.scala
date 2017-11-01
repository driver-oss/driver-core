package xyz.driver.core.rest

import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}

class RestTest extends FlatSpec with Matchers {
  "`escapeScriptTags` function" should "escap script tags properly" in {
    val dirtyString = "</sc----</sc----</sc"
    val cleanString = "--------------------"

    (escapeScriptTags(ByteString(dirtyString)).utf8String) should be(dirtyString.replace("</sc", "< /sc"))

    (escapeScriptTags(ByteString(cleanString)).utf8String) should be(cleanString)
  }
}
