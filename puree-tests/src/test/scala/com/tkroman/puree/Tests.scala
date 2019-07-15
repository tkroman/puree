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
import com.tkroman.puree.annotation.intended
import org.scalatest.funsuite.AnyFunSuite

// super-hacky, NIH compiler, NIH "logging" etc
class Tests extends AnyFunSuite {
  private final val log: Boolean = false

  private val commonOptions: List[String] = List(
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Werror",
    "-Ywarn-value-discard"
  )

  class MyGlobal(s: Settings, r: StoreReporter) extends Global(s, r) {
    override protected def loadRoughPluginsList(): List[Plugin] =
      new Puree(this) :: super.loadRoughPluginsList()

    def myReporter: StoreReporter = r
  }
  def compiler(moreOptions: List[String]): MyGlobal = {
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
      s.processArguments(
        moreOptions ++ commonOptions,
        processAll = false
      ): @intended
      s.outputDirs.setSingleOutput(new VirtualDirectory("<memory>", None))
      s.classpath.value = getSbtCompatibleClasspath
      s
    }

    new MyGlobal(settings, new StoreReporter)
  }

  lazy val strictCompiler: MyGlobal = compiler(List("-P:puree:level:strict"))
  lazy val moderateCompiler: MyGlobal = compiler(List("-P:puree:level:effects"))

  def compileFile(
      path: Path,
      pos: Boolean,
      compiler: MyGlobal
  ): Either[String, Unit] = {
    val short: String =
      path.getParent.getFileName.toString + "/" + path.getFileName.toString
    try {
      compiler.myReporter.reset()
      new compiler.Run()
        .compileSources(List(compiler.getSourceFile(path.toString)))
      if (log && compiler.myReporter.infos.nonEmpty) {
        println(
          compiler.myReporter.infos
            .map { i =>
              val filename: Path = Paths
                .get(i.pos.source.path)
                .getFileName

              s"${i.severity} $filename:${i.pos.line}:${i.pos.column} ${i.msg}"
            }
            .mkString("\n")
        )
      }
      val msg: String = compiler.myReporter.infos.map(_.msg).mkString("\n")
      val hasErrors: Boolean = compiler.myReporter.hasErrors
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
          s"Expected compilation of $short to succeed, failed instead:\n${e.getMessage}"
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
      pos: Boolean,
      compiler: MyGlobal
  ): List[(Path, Either[String, Unit])] = {
    fs.map(p => p -> compileFile(p, pos, compiler))
  }

  def mkTests(xs: List[(Path, Either[String, Unit])]): Unit = {
    def par(f: Path): String = f.getParent.getFileName.toString
    xs.foreach {
      case (p, either) =>
        test(par(p.getParent) + "/" + par(p) + "/" + p.getFileName.toString) {
          either.fold(fail(_), Function.const(succeed))
        }
    }
  }

  // TODO add "off" level tests
  mkTests(compile(ls("effects/pos"), pos = true, moderateCompiler))
  mkTests(compile(ls("effects/neg"), pos = false, moderateCompiler))

  mkTests(compile(ls("strict/pos"), pos = true, strictCompiler))
  mkTests(compile(ls("strict/neg"), pos = false, strictCompiler))
}
