import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import sbt.Keys._
import sbt._
import wartremover.WartRemover.autoImport._
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.{GitBranchPrompt, GitVersioning}
import org.scalafmt.sbt.ScalaFmtPlugin.autoImport._
import sbtrelease.{Version, _}
// we hide the existing definition for setReleaseVersion to replace it with our own
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations.{setReleaseVersion => _}


object DriverConfigurations {

  lazy val wartRemoverSettings = Seq(
    wartremoverErrors in (Compile, compile) ++= Warts.allBut(
      Wart.AsInstanceOf, Wart.Nothing, Wart.Overloading, Wart.DefaultArguments, Wart.Any,
      Wart.Option2Iterable, Wart.ExplicitImplicitTypes, Wart.Throw, Wart.ToString)
  )

  lazy val acyclicSettings = Seq(
    autoCompilerPlugins := true,
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.4")
  )

  lazy val scalafmtSettings = Seq(
    scalafmtConfig in ThisBuild := Some(file(".scalafmt")),
    testExecution in (Test, test) <<=
      (testExecution in (Test, test)) dependsOn (scalafmtTest in Compile, scalafmtTest in Test)
  )

  lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

  def publicationSettings() = Seq(
    publishTo := Some(Resolver.file("file", new File("releases")))
    // publishTo := { // TODO: For actual Driver jar repo
    //   val nexus = "https://my.artifact.repo.net/"
    //   if (isSnapshot.value)
    //     Some("snapshots" at nexus + "content/repositories/snapshots")
    //   else
    //     Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    // }
  )

  def releaseSettings() = {
    def setVersionOnly(selectVersion: Versions => String): ReleaseStep =  { st: State =>
      val vs = st.get(ReleaseKeys.versions).getOrElse(
        sys.error("No versions are set! Was this release part executed before inquireVersions?"))
      val selected = selectVersion(vs)

      st.log.info("Setting version to '%s'." format selected)
      val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

      reapply(Seq(
        if (useGlobal) version in ThisBuild := selected else version := selected
      ), st)
    }

    lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

    Seq(
      releaseIgnoreUntrackedFiles := true,
      // Check http://blog.byjean.eu/2015/07/10/painless-release-with-sbt.html for details
      releaseVersionBump := sbtrelease.Version.Bump.Minor,
      releaseVersion <<= releaseVersionBump(bumper => {
        ver => Version(ver)
          .map(_.withoutQualifier)
          .map(_.bump(bumper).string).getOrElse(versionFormatError)
      }),
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runTest, // probably, runTest after setReleaseVersion, if tests depend on version
        setReleaseVersion,
        commitReleaseVersion, // performs the initial git checks
        tagRelease,
        publishArtifacts,
        setNextVersion,
        commitNextVersion,
        pushChanges // also checks that an upstream branch is properly configured
      )
    )
  }

  implicit class driverConfigurations(project: Project) {

    def gitPluginConfiguration: Project = {
      val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

      project
        .enablePlugins(GitVersioning, GitBranchPrompt)
        .settings(
          git.useGitDescribe := true,
          git.baseVersion := "0.0.0",
          git.gitTagToVersionNumber := {
            case VersionRegex(v, "SNAPSHOT") => Some(s"$v-SNAPSHOT")
            case VersionRegex(v, "") => Some(v)
            case VersionRegex(v, s) => Some(s"$v-$s-SNAPSHOT")
            case _ => None
          })
    }
  }
}