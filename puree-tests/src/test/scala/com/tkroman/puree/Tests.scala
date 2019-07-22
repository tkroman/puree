package com.tkroman.puree

import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._
import com.tkroman.puree.testsupport.{PureeGlobal, TestResult}
import org.scalatest.funsuite.AnyFunSuite

class Tests extends AnyFunSuite {
  mkTests("effects/pos", PureeGlobal.moderateCompiler)
  mkTests("effects/neg", PureeGlobal.moderateCompiler)
  mkTests("strict/pos", PureeGlobal.strictCompiler)
  mkTests("strict/neg", PureeGlobal.strictCompiler)

  private def mkTests(dir: String, compiler: PureeGlobal): Unit = {
    mkTests(compileAll(ls(dir), pos = dir.endsWith("/pos"), compiler))
  }

  private def mkTests(xs: List[(Path, () => TestResult)]): Unit = {
    xs.foreach {
      case (file, testBody) =>
        val nc: Int = file.getNameCount
        test(file.subpath(nc - 3, nc).toString)(testBody().get)
    }
  }

  private def ls(dir: String): List[Path] = {
    Files
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
      .filterNot(_.getFileName.endsWith(".ignore"))
  }

  def compileAll(
      fs: List[Path],
      pos: Boolean,
      compiler: PureeGlobal
  ): List[(Path, () => TestResult)] = {
    fs.map(p => p -> (() => compiler.compileFile(p, pos)))
  }
}
