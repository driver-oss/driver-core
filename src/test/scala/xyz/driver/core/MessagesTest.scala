package xyz.driver.core

import java.util.Locale

import com.typesafe.config.{ConfigException, ConfigFactory}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import xyz.driver.core.logging.Logger
import xyz.driver.core.messages.Messages

import scala.collection.JavaConversions._

class MessagesTest extends FlatSpec with Matchers with MockitoSugar {

  val englishLocaleMessages =
    Map("en.greeting" -> "Hello {0}!", "en.greetingFullName" -> "Hello {0} {1} {2}!", "en.hello" -> "Hello world!")

  "Messages" should "read messages from config and format with parameters" in {

    val log            = mock[Logger]
    val messagesConfig = ConfigFactory.parseMap(englishLocaleMessages)

    val messages = Messages.messages(messagesConfig, log, Locale.US)

    messages("hello") should be("Hello world!")
    messages("greeting", "Homer") should be("Hello Homer!")
    messages("greetingFullName", "Homer", "J", "Simpson") should be("Hello Homer J Simpson!")
  }

  it should "be able to read messages for different locales" in {

    val log = mock[Logger]

    val messagesConfig = ConfigFactory.parseMap(
      englishLocaleMessages ++ Map(
        "zh.hello"            -> "你好，世界!",
        "zh.greeting"         -> "你好，{0}!",
        "zh.greetingFullName" -> "你好，{0} {1} {2}!"
      ))

    val englishMessages    = Messages.messages(messagesConfig, log, Locale.US)
    val englishMessagesToo = Messages.messages(messagesConfig, log, Locale.ENGLISH)
    val chineseMessages    = Messages.messages(messagesConfig, log, Locale.CHINESE)

    englishMessages("hello") should be("Hello world!")
    englishMessages("greeting", "Homer") should be("Hello Homer!")
    englishMessages("greetingFullName", "Homer", "J", "Simpson") should be("Hello Homer J Simpson!")

    englishMessagesToo("hello") should be(englishMessages("hello"))
    englishMessagesToo("greeting", "Homer") should be(englishMessages("greeting", "Homer"))
    englishMessagesToo("greetingFullName", "Homer", "J", "Simpson") should be(
      englishMessages("greetingFullName", "Homer", "J", "Simpson"))

    chineseMessages("hello") should be("你好，世界!")
    chineseMessages("greeting", "Homer") should be("你好，Homer!")
    chineseMessages("greetingFullName", "Homer", "J", "Simpson") should be("你好，Homer J Simpson!")
  }

  it should "raise exception when locale is not available" in {

    val log            = mock[Logger]
    val messagesConfig = ConfigFactory.parseMap(englishLocaleMessages)

    an[ConfigException.Missing] should be thrownBy
      Messages.messages(messagesConfig, log, Locale.GERMAN)
  }

  it should "log a problem, when there is no message for key" in {

    val log            = mock[Logger]
    val messagesConfig = ConfigFactory.parseMap(englishLocaleMessages)

    val messages = Messages.messages(messagesConfig, log, Locale.US)

    messages("howdy") should be("howdy")

    verify(log).error(s"Message with key 'howdy' not found for locale 'en'")
  }

  it should "be able to read nested keys in multiple forms" in {
    val log = mock[Logger]

    val configString =
      """
        | en {
        |   foo.bar = "Foo Bar"
        |
        |   baz {
        |     boo = "Baz Boo"
        |     booFormat = "Baz Boo {0}"
        |   }
        | }
      """.stripMargin

    val messagesConfig = ConfigFactory.parseString(configString)

    val messages = Messages.messages(messagesConfig, log, Locale.US)

    messages("foo.bar") should be("Foo Bar")
    messages("baz.boo") should be("Baz Boo")
    messages("baz.booFormat", "Test") should be("Baz Boo Test")
  }
}
