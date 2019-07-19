package com.tkroman.puree

import java.net.URL
import scala.collection.immutable.Map
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.{BufferedSource, Source}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

final case class PureeLevels(
    detailed: Map[String, PureeLevel],
    default: PureeLevel
)

object PureeLevels {
  private type Levels = Map[String, PureeLevel]
  private type Res = Either[String, Levels]
  private type MB = mutable.Map[PureeLevel, ListBuffer[String]]

  def apply(default: PureeLevel): Either[String, PureeLevels] = {
    levels.map(PureeLevels(_, default))
  }

  private lazy val levels: Either[String, Map[String, PureeLevel]] = {
    getClass.getClassLoader
      .getResources("puree-settings")
      .asScala
      .toList
      .map { res =>
        for {
          s <- readResource(res)
          c <- parse(s)
        } yield {
          c
        }
      }
      .fold[Res](Right(Map.empty)) {
        case (Right(l), Right(r)) => Right(l ++ r)
        case (_, Left(e))         => Left(e)
        case (Left(e), _)         => Left(e)
      }
  }

  private def readResource(res: URL): Either[String, List[String]] = {
    val s: BufferedSource = Source.fromURL(res, "UTF-8")
    try {
      Right(s.getLines().toList)
    } catch {
      case NonFatal(e) => Left(e.getMessage)
    } finally {
      s.close()
    }
  }

  private def parse(config: List[String]): Res = {
    try {
      // mutable & iteratorey, but very short and hopefully readable
      val entries: Iterator[String] = config.iterator
        .filterNot(_.isEmpty)
        .filterNot(_.startsWith("#"))

      if (entries.isEmpty) {
        Right(Map.empty)
      } else {
        val mb = Map.newBuilder[String, PureeLevel]
        var lvl: PureeLevel = initCfg(entries)
        entries.foreach { entry =>
          parseLine(entry) match {
            case Left(pl)   => lvl = pl
            case Right(cfg) => mb += (cfg -> lvl)
          }
        }
        Right(mb.result())
      }
    } catch {
      case t: Throwable =>
        Left(t.getMessage)
    }
  }

  private def parseLine(s: String): Either[PureeLevel, String] = {
    s.trim match {
      case "[off]"    => Left(PureeLevel.Off)
      case "[effect]" => Left(PureeLevel.Effect)
      case "[strict]" => Left(PureeLevel.Strict)
      case entry      => Right(entry)
    }
  }

  private def initCfg(entries: Iterator[String]): PureeLevel = {
    val hdLine: Either[PureeLevel, String] = parseLine(entries.next())
    hdLine match {
      case Left(lvl) => lvl
      case Right(_) =>
        throw new IllegalArgumentException(
          "config must define a level first"
        )
    }
  }
}
