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
    "org.scalatest" %% "scalatest" % "3.1.0-SNAP11" % Test,
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % Test,
  ),
  Test / scalacOptions ++= {
    val jar = (puree / Compile / packageBin).value
    Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
  },
  Test / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq.empty
      case _ => Seq(
        "-feature",
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
  .aggregate(puree, pureeTests)
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

lazy val pureeTests = (project in file("puree-tests"))
  .dependsOn(puree, puree % "test->test")
  .settings(
    name := "puree-tests",
    crossScalaVersions := List(scala211, scala212),
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-compiler" % scalaVersion.value % Test
    ),
    // WORKAROUND https://github.com/ensime/pcplod/issues/12
    fork in Test := true,
  )
  .settings(testSettings)
