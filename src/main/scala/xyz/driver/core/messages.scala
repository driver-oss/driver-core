package xyz.driver.core

import java.util.Locale

import com.typesafe.config.{Config, ConfigException}
import com.typesafe.scalalogging.Logger

/**
  * Scala internationalization (i18n) support
  */
package messages {

  object Messages {
    def messages(config: Config, log: Logger, locale: Locale = Locale.US): Messages = {
      val map = config.getConfig(locale.getLanguage)
      Messages(map, locale, log)
    }
  }

  final case class Messages(map: Config, locale: Locale, log: Logger) {

    /**
      * Returns message for the key
      *
      * @param key key
      * @return message
      */
    def apply(key: String): String = {
      try {
        map.getString(key)
      } catch {
        case _: ConfigException =>
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
