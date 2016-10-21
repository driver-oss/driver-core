package xyz.driver.core

import java.util.Locale

import com.typesafe.config.Config
import xyz.driver.core.logging.Logger

import scala.collection.JavaConverters._

/**
  * Scala internationalization (i18n) support
  */
object messages {

  object Messages {
    def messages(config: Config, log: Logger, locale: Locale = Locale.US): Messages = {
      val map = config.getConfig(locale.getLanguage).root().unwrapped().asScala.mapValues(_.toString).toMap
      Messages(map, locale, log)
    }
  }

  final case class Messages(map: Map[String, String], locale: Locale, log: Logger) {

    /**
      * Returns message for the key
      *
      * @param key key
      * @return message
      */
    def apply(key: String): String = {
      map.get(key) match {
        case Some(message) => message
        case None =>
          log.error(s"Message with key '$key' not found for locale '${locale.getLanguage}'")
          key
      }
    }

    /**
      * Returns message for the key and formats that with parameters
      *
      * @example "Hello {0}!" with "Joe" will be "Hello Joe!"
      *
      * @param key key
      * @param params params to be embedded
      * @return formatted message
      */
    def apply(key: String, params: Any*): String = {

      def format(formatString: String, params: Seq[Any]) =
        params.zipWithIndex.foldLeft(formatString) {
          case (res, (value, index)) => res.replace(s"{$index}", value.toString)
        }

      val template = apply(key)
      format(template, params)
    }
  }
}
