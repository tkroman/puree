package com.tkroman.puree.testsupport

import java.net.URLClassLoader
import java.nio.file.{Path, Paths}
import scala.collection.immutable.ArraySeq
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.util.ClassPath
import scala.tools.nsc.{Global, Settings}
import com.tkroman.puree.Puree
import com.tkroman.puree.annotation.intended

class PureeGlobal(s: Settings, r: StoreReporter) extends Global(s, r) {
  private final val log: Boolean = false

  override protected def loadRoughPluginsList(): List[Plugin] =
    new Puree(this) :: super.loadRoughPluginsList()

  def pureeReporter: StoreReporter = r

  def compileFile(
      path: Path,
      pos: Boolean
  ): Either[String, Unit] = {
    val short: String =
      s"${path.getParent.getFileName.toString}/${path.getFileName.toString}"
    try {
      pureeReporter.reset()
      new Run()
        .compileSources(List(getSourceFile(path.toString)))
      if (log && pureeReporter.infos.nonEmpty) {
        println(
          pureeReporter.infos
            .map { i =>
              val filename: Path = Paths
                .get(i.pos.source.path)
                .getFileName

              s"${i.severity} $filename:${i.pos.line}:${i.pos.column} ${i.msg}"
            }
            .mkString("\n")
        )
      }
      val msg: String = pureeReporter.infos.map(_.msg).mkString("\n")
      val hasErrors: Boolean = pureeReporter.hasErrors
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
}

object PureeGlobal {
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

  private def compiler(moreOptions: List[String]): PureeGlobal = {
    val settings: Settings = {
      def getSbtCompatibleClasspath: String = {
        val loader: URLClassLoader =
          getClass.getClassLoader.asInstanceOf[URLClassLoader]
        val entries: Array[String] = loader.getURLs.map(_.getPath)
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

    new PureeGlobal(settings, new StoreReporter)
  }

  lazy val strictCompiler: PureeGlobal = compiler(
    List("-P:puree:level:strict")
  )

  lazy val moderateCompiler: PureeGlobal = compiler(
    List("-P:puree:level:effects")
  )
}
