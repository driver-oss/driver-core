import com.typesafe.tools.mima.plugin.MimaPlugin
import sbt.{Def, _}
import sbt.Keys._

/** This plugin extends the Migration Manager (MiMa) Plugin with common settings
  * for driver-core projects.
  */
object MiMaSettings extends AutoPlugin {

  override def requires = MimaPlugin
  override def trigger  = allRequirements

  object autoImport {
    val abiVersion = settingKey[String]("Previous version of binary-compatible projects")
    val checkAbi   = taskKey[Unit]("Check ABI compatibility with declared abiVersion")
  }
  import autoImport._
  import MimaPlugin.autoImport._

  override def buildSettings: Seq[Def.Setting[_]] = Seq(abiVersion := "")
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    mimaPreviousArtifacts := Set(
      "xyz.driver" %% name.value % abiVersion.value
    ),
    checkAbi := mimaReportBinaryIssues.value
  )

}
