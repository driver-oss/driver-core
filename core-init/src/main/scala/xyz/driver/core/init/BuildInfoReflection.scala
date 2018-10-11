package xyz.driver.core
package init

import scala.reflect.runtime
import scala.util.Try
import scala.util.control.NonFatal

/** Utility object to retrieve fields from static build configuration objects. */
private[init] object BuildInfoReflection {

  final val BuildInfoName = "xyz.driver.BuildInfo"

  lazy val name: String            = find[String]("name").getOrElse("unknown")
  lazy val version: Option[String] = find[String]("version")

  /** Lookup a given field in the build configuration. This field is required to exist. */
  private def get[A](fieldName: String): A =
    try {
      val mirror   = runtime.currentMirror
      val module   = mirror.staticModule(BuildInfoName)
      val instance = mirror.reflectModule(module).instance
      val accessor = module.info.decl(mirror.universe.TermName(fieldName)).asMethod
      mirror.reflect(instance).reflectMethod(accessor).apply().asInstanceOf[A]
    } catch {
      case NonFatal(err) =>
        throw new RuntimeException(
          s"Cannot find field name '$fieldName' in $BuildInfoName. Please define (or generate) a singleton " +
            s"object with that field. Alternatively, in order to avoid runtime reflection, you may override the " +
            s"caller with a static value.",
          err
        )
    }

  /** Try finding a given field in the build configuration. If the field does not exist, None is returned. */
  private def find[A](fieldName: String): Option[A] = Try { get[A](fieldName) }.toOption

}
