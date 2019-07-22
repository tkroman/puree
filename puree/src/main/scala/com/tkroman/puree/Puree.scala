package com.tkroman.puree

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin
import com.tkroman.puree.Puree._

object Puree {
  private val Name = "puree"
  private val DefaultLevel = "effects"
  private val LevelKey: String = "level"
  private val AllLevels: Map[String, PureeLevel] = Map(
    "off" -> PureeLevel.Off,
    DefaultLevel -> PureeLevel.Effect,
    "strict" -> PureeLevel.Strict
  )
  private val Usage: String =
    s"Available choices: ${AllLevels.keySet.mkString("|")}. Usage: -P:$Name:$LevelKey:$$LEVEL"

}

class Puree(val global: Global) extends Plugin {
  override val name: String = Name
  override val description: String = "Warn about unused effects"

  private var globalLevel: PureeLevel = _
  private var config: PureeConfig = _

  def getConfig: PureeConfig = config

  override def init(options: List[String], error: String => Unit): Boolean = {
    val suggestedLevel: Option[String] = options
      .find(_.startsWith(LevelKey))
      .map(_.stripPrefix(s"$LevelKey:"))

    suggestedLevel match {
      case Some(s) if AllLevels.isDefinedAt(s) =>
        globalLevel = AllLevels(s)
      case Some(s) =>
        error(s"Puree: invalid strictness level [$s]. $Usage")
      case None =>
      // default
    }

    PureeConfig(globalLevel) match {
      case Right(ok) =>
        config = ok
        defaultEnabled || atLeastOneIndividualEnabled
      case Left(err) =>
        error(err)
        false
    }

  }

  def getLevel(x: Option[String]): PureeLevel = {
    x match {
      case Some(x) =>
        config.detailed.getOrElse(x, config.global)
      case None =>
        config.global
    }
  }

  override lazy val components: List[UnusedDetector] = List(
    new UnusedDetector(this, global)
  )

  private def atLeastOneIndividualEnabled: Boolean = {
    config.detailed.exists(_._2 != PureeLevel.Off)
  }

  private def defaultEnabled: Boolean = {
    globalLevel != PureeLevel.Off
  }

}
