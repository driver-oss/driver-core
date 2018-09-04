package xyz.driver.core
package init

import java.nio.file.{Files, Path, Paths}

import com.google.auth.oauth2.ServiceAccountCredentials

sealed trait Platform
object Platform {
  case class GoogleCloud(keyfile: Path, namespace: String) extends Platform {
    def credentials: ServiceAccountCredentials = ServiceAccountCredentials.fromStream(
      Files.newInputStream(keyfile)
    )
    def project: String = credentials.getProjectId
  }
  // case object AliCloud   extends Platform
  case object Dev extends Platform

  lazy val fromEnv: Platform = {
    def isGoogle = sys.env.get("GOOGLE_APPLICATION_CREDENTIALS").map { value =>
      val keyfile = Paths.get(value)
      require(Files.isReadable(keyfile), s"Google credentials file $value is not readable.")
      val namespace = sys.env.getOrElse("SERVICE_NAMESPACE", sys.error("Namespace not set"))
      GoogleCloud(keyfile, namespace)
    }
    isGoogle.getOrElse(Dev)
  }

  def current: Platform = fromEnv

}
