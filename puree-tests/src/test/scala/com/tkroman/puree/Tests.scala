package com.tkroman.puree

import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.collection.immutable.ArraySeq
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.{Global, Settings}
import org.scalatest.funsuite.AnyFunSuite

// super-hacky, NIH compiler, NIH "logging" etc
class Tests extends AnyFunSuite {
  private final val log: Boolean = false

  private val options: List[String] = List(
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
        val sclpath: Option[String] = entries
          .find(_.endsWith("scala-compiler.jar"))
          .map(_.replaceAll("scala-compiler.jar", "scala-library.jar"))
        ClassPath.join(ArraySeq.unsafeWrapArray(entries ++ sclpath): _*)
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
    val short: String =
      path.getParent.getFileName.toString + "/" + path.getFileName.toString
    try {
      compiler.getr.reset()
      new compiler.Run()
        .compileSources(List(compiler.getSourceFile(path.toString)))
      if (log && compiler.getr.infos.nonEmpty) {
        println(
          compiler.getr.infos
            .map { i =>
              val filename: Path = Paths
                .get(i.pos.source.path)
                .getFileName

              s"${i.severity} $filename:${i.pos.line}:${i.pos.column} ${i.msg}"
            }
            .mkString("\n")
        )
      }
      val msg: String = compiler.getr.infos.map(_.msg).mkString("\n")
      val hasErrors: Boolean = compiler.getr.hasErrors
      if (hasErrors) {
        throw new Exception(msg)
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

  private def ls(dir: String): List[Path] = {
    import scala.jdk.CollectionConverters._
    val allFiles = Files
      .list(
        Paths.get(
          Thread
            .currentThread()
            .getContextClassLoader
            .getResource(dir)
            .getPath
        )
      )
      .collect(Collectors.toList[Path])
      .asScala
      .toList
      .filterNot(_.getFileName.toString.endsWith(".ignore"))

    val only: List[Path] =
      allFiles.filter(_.getFileName.toString.endsWith(".only"))

    if (only.nonEmpty) {
      only
    } else {
      allFiles
    }
  }

  def compile(
      fs: List[Path],
      pos: Boolean
  ): List[(Path, Either[String, Unit])] = {
    fs.map(p => p -> compileFile(p, pos))
  }

  def mkTests(xs: List[(Path, Either[String, Unit])]): Unit = {
    xs.foreach {
      case (p, either) =>
        test(p.getParent.getFileName.toString + "/" + p.getFileName.toString) {
          either.fold(fail(_), Function.const(succeed))
        }
    }
  }

  // TODO add off/strict tests
  mkTests(compile(ls("pos"), pos = true))
  mkTests(compile(ls("neg"), pos = false))
}
