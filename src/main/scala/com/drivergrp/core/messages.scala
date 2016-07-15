package com.drivergrp.core


import java.util.Locale

import com.drivergrp.core.config.ConfigModule
import com.drivergrp.core.logging.{Logger, LoggerModule}

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap

/**
  * Scala internationalization (i18n) support
  */
object messages {

  trait MessagesModule {

    def messages: Messages
  }

  trait ConfigMessagesModule extends MessagesModule {
    this: ConfigModule with LoggerModule =>

    private val loadedFromConfig = new TrieMap[Locale, Messages]()
    val locale: Locale = Locale.US

    val messages: Messages = {
      loadedFromConfig.getOrElseUpdate(locale, {
        val map = config.getConfig(locale.getISO3Language).root().unwrapped().asScala.mapValues(_.toString).toMap
        Messages(map, locale, log)
      })
    }
  }


  case class Messages(map: Map[String, String], locale: Locale, log: Logger) {

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
          log.error(s"Message with key $key not found for locale " + locale.getDisplayName)
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
          case (res, (value, index)) => res.replaceAll(s"{$index}", value.toString)
        }

      val template = apply(key)
      format(template, params)
    }
  }
}