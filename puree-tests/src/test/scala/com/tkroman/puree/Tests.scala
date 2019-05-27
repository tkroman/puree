package com.tkroman.puree

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.{Global, Settings}
import org.scalatest.funsuite.AnyFunSuite

class Tests extends AnyFunSuite {
  private val options = List(
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Xfatal-warnings",
    "-Ywarn-value-discard"
  )

  class MyGlobal(s: Settings, r: StoreReporter) extends Global(s, r) {
    override protected def loadRoughPluginsList(): List[Plugin] =
      new Puree(this) :: super.loadRoughPluginsList()

    def getr: StoreReporter = r
  }
  val compiler: MyGlobal = {
    val settings: Settings = {
      def getSbtCompatibleClasspath: String = {
        val loader: URLClassLoader =
          getClass.getClassLoader.asInstanceOf[URLClassLoader]
        val entries: Array[String] = loader.getURLs map (_.getPath)
        val sclpath: Option[String] = entries find (_.endsWith(
          "scala-compiler.jar"
        )) map {
          _.replaceAll("scala-compiler.jar", "scala-library.jar")
        }
        ClassPath.join(entries ++ sclpath: _*)
      }

      val s = new Settings()
      // puree @ work :)
      val _ = s.processArguments(options, processAll = true)
      s.outputDirs.setSingleOutput(new VirtualDirectory("<memory>", None))
      s.classpath.value = getSbtCompatibleClasspath
      s
    }

    new MyGlobal(settings, new StoreReporter)
  }

  def compileFile(path: Path, pos: Boolean): Either[String, Unit] = {
    val short = path.subpath(path.getNameCount - 2, path.getNameCount).toString
    try {
      new compiler.Run()
        .compileSources(List(compiler.getSourceFile(path.toString)))
      if (compiler.getr.hasErrors) {
        throw new Exception(compiler.getr.infos.map(_.msg).mkString("\n"))
      }
      if (pos) {
        Right(())
      } else {
        Left(s"Expected compilation of $short to fail, succeeded instead")
      }
    } catch {
      case e: Exception if pos =>
        Left(
          s"Expected compilation of $short to succeed, failed instead: ${e.getMessage}"
        )
      case _: Exception =>
        Right(())

    }
  }

  private def compileAll() = {
    import scala.collection.JavaConverters._
    val pos = Files
      .list(
        Paths.get(
          Thread
            .currentThread()
            .getContextClassLoader
            .getResource("pos")
            .getPath
        )
      )
      .collect(Collectors.toList[Path])
      .asScala
      .toList

    val neg = Files
      .list(
        Paths.get(
          Thread
            .currentThread()
            .getContextClassLoader
            .getResource("neg")
            .getPath
        )
      )
      .collect(Collectors.toList[Path])
      .asScala
      .toList

    pos
      .map(p => p -> compileFile(p, true))
      .foreach {
        case (p, Left(err)) =>
          test(p.subpath(p.getNameCount - 2, p.getNameCount).toString) {
            fail(err)
          }
        case (p, Right(_)) =>
          test(p.subpath(p.getNameCount - 2, p.getNameCount).toString) {
            succeed
          }
      }

    neg
      .map(p => p -> compileFile(p, false))
      .foreach {
        case (p, Left(err)) =>
          test(p.subpath(p.getNameCount - 2, p.getNameCount).toString) {
            fail(err)
          }
        case (p, Right(_)) =>
          test(p.subpath(p.getNameCount - 2, p.getNameCount).toString) {
            succeed
          }
      }
  }

  compileAll()
}
