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

  object GoogleCloud {
    lazy val fromEnv: GoogleCloud = {
      val credentialsFile =
        sys.env.getOrElse(
          "GOOGLE_APPLICATION_CREDENTIALS",
          sys.error("Expected GOOGLE_APPLICATION_CREDENTIALS file to be set in gcp environment"))

      val keyfile = Paths.get(credentialsFile)
      require(Files.isReadable(keyfile), s"Google credentials file $credentialsFile is not readable.")

      val namespace = sys.env.getOrElse("SERVICE_NAMESPACE", sys.error("Namespace not set"))
      GoogleCloud(keyfile, namespace)
    }
  }

  case class AliCloud(accountId: String, accessId: String, accessKey: String, region: String, namespace: String)
      extends Platform

  object AliCloud {
    lazy val fromEnv: AliCloud = {
      AliCloud(
        accountId = sys.env("ALICLOUD_ACCOUNT_ID"),
        accessId = sys.env("ALICLOUD_ACCESS_ID"),
        accessKey = sys.env("ALICLOUD_ACCESS_KEY"),
        region = sys.env("CLOUD_REGION"),
        namespace = sys.env("SERVICE_NAMESPACE")
      )
    }
  }

  case object Dev extends Platform

  lazy val fromEnv: Platform = {
    sys.env.get("CLOUD_PROVIDER") match {
      case Some("alicloud") => AliCloud.fromEnv
      case Some("gcp")      => GoogleCloud.fromEnv

      // For backwards compat, try instantiating GCP first, falling back to Dev
      case _ => util.Try(GoogleCloud.fromEnv).getOrElse(Dev)
    }
  }

  def current: Platform = fromEnv

}
