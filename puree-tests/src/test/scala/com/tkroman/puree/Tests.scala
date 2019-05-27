package com.tkroman.puree

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.stream.Collectors
import scala.tools.reflect.{FrontEnd, ToolBoxError}
import org.scalatest.funsuite.AnyFunSuite

class Tests extends AnyFunSuite {
  private val pluginJar =
    Paths
      .get(
        Thread
          .currentThread()
          .getContextClassLoader
          .getResource(".")
          .getPath
      )
      .resolve("../../../../puree/target/scala-2.12/puree_2.12-0.0.1.jar")
      .normalize()
      .toAbsolutePath

  private val options = List(
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Xfatal-warnings",
    "-Ywarn-value-discard",
    // yuck
    s"-Xplugin:${pluginJar.toString}",
    s"-Jdummy=${System.currentTimeMillis()}"
  )
  import scala.reflect.runtime.{universe => ru}
  import scala.tools.reflect.ToolBox
  val tb = ru
    .runtimeMirror(Thread.currentThread().getContextClassLoader)
    .mkToolBox(
      frontEnd = new FrontEnd {
        override def display(info: Info): Unit = ()
        override def interactive(): Unit = ()
      },
      options = options.mkString(" ")
    )

  def compileFile(path: Path, pos: Boolean): Either[String, Unit] = {
    val short = path.subpath(path.getNameCount - 2, path.getNameCount).toString
    try {
      val content = new String(
        Files.readAllBytes(path),
        StandardCharsets.UTF_8
      )
      val _ = tb.compile(
        tb.parse(
          s"object test { $content } ; test"
        )
      )
      if (pos) {
        Right(())
      } else {
        Left(s"Expected compilation of $short to fail, succeeded instead")
      }
    } catch {
      case e: ToolBoxError if pos =>
        Left(
          s"Expected compilation of $short to succeed, failed instead: ${e.getMessage}"
        )
      case _: ToolBoxError =>
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
