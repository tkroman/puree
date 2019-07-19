package com.tkroman.puree

import java.util.concurrent.ConcurrentHashMap
import java.util.function
import scala.annotation.tailrec
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}
import com.tkroman.puree.annotation.intended

class Puree(val global: Global) extends Plugin {
  override val name: String = "puree"
  override val description: String = "Warn about unused effects"

  private val DefaultLevel = "effects"
  private val LevelKey: String = "level"
  private val AllLevels: Map[String, PureeLevel] = Map(
    "off" -> PureeLevel.Off,
    DefaultLevel -> PureeLevel.Effect,
    "strict" -> PureeLevel.Strict
  )
  private val Usage: String =
    s"Available choices: ${AllLevels.keySet.mkString("|")}. Usage: -P:$name:$LevelKey:$$LEVEL"

  private var level: PureeLevel = PureeLevel.Effect
  private var config: PureeLevels = PureeLevels(Map.empty, PureeLevel.Effect)

  override def init(options: List[String], error: String => Unit): Boolean = {
    val suggestedLevel: Option[String] = options
      .find(_.startsWith(LevelKey))
      .map(_.stripPrefix(s"$LevelKey:"))

    suggestedLevel match {
      case Some(s) if AllLevels.isDefinedAt(s) =>
        level = AllLevels(s)
      case Some(s) =>
        error(
          s"Puree: invalid strictness level [$s]. $Usage"
        )
      case None => // default
    }

    PureeLevels(level) match {
      case Right(ok) =>
        config = ok
        (level != PureeLevel.Off) || (
          config.detailed.nonEmpty && config.detailed.exists(
            _._2 != PureeLevel.Off
          )
        )
      case Left(err) =>
        error(err)
        false
    }

  }

  def getLevel(x: Option[String]): PureeLevel = {
    x match {
      case Some(x) =>
        config.detailed.getOrElse(x, config.default)
      case None =>
        config.default
    }
  }

  override lazy val components: List[UnusedEffectDetector] = List(
    new UnusedEffectDetector(this, global)
  )
}

class UnusedEffectDetector(puree: Puree, val global: Global)
    extends PluginComponent {
  import global._

  override val runsAfter: List[String] = List("typer")
  override val runsBefore: List[String] = List("patmat")
  override val phaseName: String = "puree-checker"

  private val UnitType: Type = typeOf[Unit]

  // avoid expensive lookups
  private val cache: ConcurrentHashMap[String, Option[Type]] =
    new ConcurrentHashMap[String, Option[Type]]()

  override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
    override def apply(unit: CompilationUnit): Unit = {
      val tt: Traverser = new Traverser {
        override def traverse(tree: Tree): Unit = {
          val descend: Boolean = tree match {
            case t if intended(t) =>
              false
            case Template(_, _, body) =>
              noSuspiciousEffects(body)
            case Block(stats, _) =>
              noSuspiciousEffects(stats)
            case _ =>
              true
          }
          if (descend) {
            super.traverse(tree)
          }
        }
      }
      tt.traverse(unit.body)
    }
  }

  private def getEffect(a: Tree): Option[Type] = {
    a match {
      case _ if isSuperConstructorCall(a) =>
        // in constructors, calling super.<init>
        // when super is an F[_, _*] is seen as an
        // unassigned effectful value :(
        None

      case _ =>
        Option(a.tpe).flatMap { tpe =>
          cache.computeIfAbsent(
            tpe.safeToString,
            new function.Function[String, Option[Type]] {
              override def apply(t: String): Option[Type] = {
                val scrName: Option[String] = scrutineeFullName(a)
                if (isOff(scrName)) {
                  None
                } else if (isStrict(scrName) && !(tpe =:= UnitType)) {
                  // under strict settings we abort on any non-unit method
                  // (assuming unit is always side-effecting)
                  Some(tpe)
                } else if (tpe.typeSymbol.typeParams.nonEmpty) {
                  Some(tpe)
                } else {
                  val bts: List[Type] = tpe.baseTypeSeq.toList
                  // looking at basetypeseq b/c None is an Option[A]
                  if (bts.exists(bt => bt.typeSymbol.isSealed)) {
                    bts.find(bt => bt.typeSymbol.typeParams.nonEmpty)
                  } else {
                    // Only F-bounded because if we just look for
                    // ANY non-empty F[_] in baseTypeSeq b/c
                    // e.g. String is Comparable[String] :/
                    // FIXME: is it better to list (and allow for configuration)
                    // FIXME: the set of "ok" F[_]s? Comparable etc
                    bts.find(_.typeSymbol.typeParams.exists(_.isFBounded))
                  }
                }
              }
            }
          )
        }
    }
  }

  private def scrutineeFullName(a: global.Tree): Option[String] = {
    a match {
      case Apply(s: Select, _) =>
        Some(
          s.qualifier.symbol.tpe.typeSymbol.fullName + "." + s.symbol.nameString
        )
      case s: Select =>
        Some(
          s.qualifier.symbol.tpe.typeSymbol.fullName + "." + s.symbol.nameString
        )
      case i: Ident =>
        Some(
          i.qualifier.symbol.tpe.typeSymbol.fullName + "." + i.symbol.nameString
        )
      case _ =>
        None
    }
  }

  private def noSuspiciousEffects(stats: List[Tree]): Boolean = {
    def isOkUsage(e: Option[Type], warning: Type => Unit): Boolean = {
      e match {
        case Some(a) =>
          warning(a)
          false
        case None =>
          true
      }
    }

    stats.forall {
      case s if intended(s) =>
        true
      case a: Apply =>
        isOkUsage(
          getEffect(a),
          eff =>
            reporter.warning(
              a.pos,
              s"Unused effectful function call of type $eff"
            )
        )
      case t if t.isTerm =>
        isOkUsage(
          getEffect(t),
          eff =>
            reporter.warning(
              t.pos,
              s"Unused effectful member reference of type $eff"
            )
        )

      case _ =>
        true
    }
  }

  @tailrec
  private def isSuperConstructorCall(t: Tree): Boolean = {
    t match {
      case Apply(Select(Super(This(_), _), _), _) => true
      case Apply(nt, _)                           => isSuperConstructorCall(nt)
      case _                                      => false
    }
  }

  private def isStrict(x: Option[String]): Boolean = {
    puree.getLevel(x) == PureeLevel.Strict
  }

  private def isOff(x: Option[String]): Boolean = {
    puree.getLevel(x) == PureeLevel.Off
  }

  private def intended(a: Tree): Boolean = {
    def check(scrutinee: Annotatable[_]): Boolean =
      scrutinee.annotations.exists(_.tpe == typeOf[intended])

    if (a.isType && a.hasSymbolField) {
      check(a.symbol)
    } else if (a.isTerm) {
      check(a.tpe)
    } else if (a.isDef && a.hasSymbolField) {
      check(a.symbol)
    } else {
      false
    }
  }
}
