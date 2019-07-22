import xerial.sbt.Sonatype._

lazy val scala213 = "2.13.0"
lazy val scala212 = "2.12.8"
lazy val scala211 = "2.11.12"

// temporary not supporting 2.11-2.12 since i want
// to get the functionality first before doing this
lazy val supportedScalaVersions =
  // List(scala211, scala212, scala213)
  List(scala213)

ThisBuild / organization := "com.tkroman"
ThisBuild / version := "0.0.11"
ThisBuild / licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
ThisBuild / homepage := Some(url("http://github.com/tkroman/puree"))
ThisBuild / scalaVersion := scala213
ThisBuild / sonatypeProfileName := "com.tkroman"
ThisBuild / publishMavenStyle := true
ThisBuild / licenses := Seq(
  "MIT" -> url("https://github.com/tkroman/puree/blob/master/LICENSE")
)
ThisBuild / sonatypeProjectHosting := Some(
  GitHubHosting("tkroman", "puree", "rmn.tk.ml@gmail.com")
)

lazy val testSettings = Seq(
  libraryDependencies ++= Seq(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) =>
        "org.scalatest" % "scalatest_2.13.0-RC3" % "3.1.0-SNAP12" % Test
      case _ =>
        "org.scalatest" %% "scalatest" % "3.1.0-SNAP12" % Test
    }
  ),
  ThisBuild / scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) | Some((2, 12)) => Seq.empty
      case _ =>
        Seq(
          "-encoding",
          "utf-8",
          "-explaintypes",
          "-feature",
          "-language:existentials",
          "-language:experimental.macros",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-unchecked",
          "-Xcheckinit",
          // "-Xsource:2.14", // too quirky
          "-Xlint:adapted-args",
          "-Xlint:constant",
          "-Xlint:delayedinit-select",
          "-Xlint:doc-detached",
          "-Xlint:inaccessible",
          "-Xlint:infer-any",
          "-Xlint:missing-interpolator",
          "-Xlint:nullary-override",
          "-Xlint:nullary-unit",
          "-Xlint:option-implicit",
          "-Xlint:package-object-classes",
          "-Xlint:poly-implicit-overload",
          "-Xlint:private-shadow",
          "-Xlint:stars-align",
          "-Xlint:type-parameter-shadow",
          "-Xlint:nonlocal-return",
          "-Xlint:valpattern",
          "-Xlint:eta-zero",
          "-Xlint:eta-sam",
          "-Xlint:deprecation",
          "-Werror",
          "-Wdead-code",
          "-Wextra-implicit",
          "-Wself-implicit",
          "-Wnumeric-widen",
          "-Woctal-literal",
          "-Wunused:implicits",
          "-Wunused:locals",
          "-Wunused:params",
          "-Wunused:patvars",
          "-Wunused:privates",
          "-Wunused:imports",
          "-Wunused:privates",
          "-Wunused:explicits",
          "-Wunused:linted",
          "-Wvalue-discard",
        )
    }
  },
  publish / skip := true
)

lazy val pureeRoot = (project in file("."))
  .aggregate(puree, pureeApi, pureeTests)
  .settings(
    publish / skip := true,
    publishArtifact := false
  )

lazy val pureeApi = (project in file("puree-api"))
  .settings(
    name := "puree-api",
    crossScalaVersions := supportedScalaVersions,
    publishTo := sonatypePublishTo.value,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Test
    ),
    sonatypeProjectHosting := Some(
      GitHubHosting("tkroman", "puree", "rmn.tk.ml@gmail.com")
    ),
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
      false
    }
  )

lazy val puree = (project in file("puree"))
  .dependsOn(pureeApi)
  .settings(
    name := "puree",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-compiler" % scalaVersion.value
    ),
    publishTo := sonatypePublishTo.value,
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
      false
    },
    sonatypeProjectHosting := Some(
      GitHubHosting("tkroman", "puree", "rmn.tk.ml@gmail.com")
    )
  )

lazy val pureeTests = (project in file("puree-tests"))
  .dependsOn(puree, pureeApi)
  .settings(
    name := "puree-tests",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      scalaOrganization.value % "scala-compiler" % scalaVersion.value % Test
    ),
    fork in Test := true
  )
  .settings(testSettings)
