package com.tkroman.puree

import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import com.tkroman.puree.testsupport.PureeGlobal
import org.scalatest.funsuite.AnyFunSuite

class Tests extends AnyFunSuite {
  mkTests(compile(ls("effects/pos"), pos = true, PureeGlobal.moderateCompiler))
  mkTests(compile(ls("effects/neg"), pos = false, PureeGlobal.moderateCompiler))

  mkTests(compile(ls("strict/pos"), pos = true, PureeGlobal.strictCompiler))
  mkTests(compile(ls("strict/neg"), pos = false, PureeGlobal.strictCompiler))

  private def mkTests(xs: List[(Path, Either[String, Unit])]): Unit = {
    def par(f: Path): String = f.getParent.getFileName.toString
    xs.foreach {
      case (p, either) =>
        test(par(p.getParent) + "/" + par(p) + "/" + p.getFileName.toString) {
          either.fold(fail(_), Function.const(succeed))
        }
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
      compiler: PureeGlobal
  ): List[(Path, Either[String, Unit])] = {
    fs.map(p => p -> compiler.compileFile(p, pos))
  }
}
