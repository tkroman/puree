import xerial.sbt.Sonatype._

lazy val scala213 = "2.13.0-RC2"
lazy val scala212 = "2.12.8"
lazy val scala211 = "2.11.12"

lazy val supportedScalaVersions = List(scala211, scala212, scala213)

ThisBuild / organization := "com.tkroman"
ThisBuild / version := "0.0.1"
ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("http://github.com/tkroman/puree"))
ThisBuild / scalaVersion := scala212

val testSettings = Seq(
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.1.0-SNAP11" % Test
  ),
  Test / scalacOptions ++= {
    val jar = (puree / Compile / packageBin).value
    Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
  },
  Test / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq.empty
      case _ => Seq(
        "-Ywarn-unused:implicits",
        "-Ywarn-unused:imports",
        "-Ywarn-unused:locals",
        "-Ywarn-unused:params",
        "-Ywarn-unused:patvars",
        "-Ywarn-unused:privates",
        "-Xfatal-warnings",
      )
    }
  },
  publish / skip := true
)

lazy val root = (project in file("."))
  .aggregate(puree, pluginTests, pcplodTests)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true
  )

lazy val puree = (project in file("puree"))
  .settings(
    name := "puree",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-compiler" % scalaVersion.value,
    ),
    publishTo := sonatypePublishTo.value,
    publishMavenStyle := true,
    sonatypeProjectHosting := Some(GitHubHosting("tkroman", "puree", "rmn.tk.ml@gmail.com")),
  )

lazy val pluginTests = (project in file("plugin-tests"))
  .dependsOn(puree)
  .settings(crossScalaVersions := supportedScalaVersions)
  .settings(testSettings)

lazy val pcplodTests = (project in file("pcplod-tests"))
  .dependsOn(pluginTests % "compile->compile;test->test")
  .settings(
    name := "pcplod-tests",
    crossScalaVersions := List(scala211, scala212),
    libraryDependencies ++= Seq(
      "org.ensime" %% "pcplod" % "1.2.1" % Test
    ),
    // WORKAROUND https://github.com/ensime/pcplod/issues/12
    fork in Test := true,
    javaOptions in Test ++= Seq(
      s"""-Dpcplod.settings=${(scalacOptions in Test).value.filterNot(_.contains(",")).mkString(",")}""",
      s"""-Dpcplod.classpath=${(fullClasspath in Test).value.map(_.data).mkString(",")}"""
    )
  )
  .settings(testSettings)
