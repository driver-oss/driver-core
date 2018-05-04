package xyz.driver.core

import com.typesafe.config.Config
import kamon.statsd.MetricKeyGenerator

import scala.collection.immutable.TreeMap

/** Custom implementation of MetricKeyGenerator that does not include hostname or application name.
  * It is assumed a metrics collector (such as Telegraf) will differentiate between applications. */
class KamonMetricKeyGenerator(config: Config) extends MetricKeyGenerator {

  private def normalize(s: String): String =
    s.replace(": ", "-").replace(":", "-").replace(" ", "_").replace("/", "_").replace(".", "_")

  private def sortAndConcatenateTags(tags: Map[String, String]): String = {
    TreeMap(tags.toSeq: _*)
      .flatMap { case (key, value) => List(key, value) }
      .map(normalize)
      .mkString(".")
  }

  override def generateKey(name: String, tags: Map[String, String]): String = {
    val stringTags = if (tags.nonEmpty) "." + sortAndConcatenateTags(tags) else ""
    s"${normalize(name)}$stringTags"
  }

}
