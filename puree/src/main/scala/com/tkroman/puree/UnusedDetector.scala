package com.tkroman.puree

import java.util.concurrent.ConcurrentHashMap
import java.util.function
import scala.annotation.tailrec
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.{Global, Phase}
import com.tkroman.puree.annotation.intended

class UnusedDetector(puree: Puree, val global: Global) extends PluginComponent {
  import global._

  override val runsAfter: List[String] = List("typer")
  override val runsBefore: List[String] = List("patmat")
  override val phaseName: String = "puree-checker"

  private val UnitType: Type = typeOf[Unit]

  // lazy b/c init() is called _after_ this class is created
  // (or i think so based on a fact that this was not properly initialized w/o lazy)
  private lazy val configuredLevels: List[(ClassSymbol, (String, PureeLevel))] =
    puree.getConfig.detailed
      .map {
        case (e: String, l: PureeLevel) =>
          val (tpeName, memberName) = e.splitAt(e.lastIndexOf("."))
          rootMirror.getClassByName(tpeName) -> (memberName.substring(1) -> l)
      }
      .to(List)

  private type Cache[A] = ConcurrentHashMap[String, A]

  // avoid expensive lookups per type
  // (e.g. for some types we do baseTypeSeq lookups
  // to figure out if they look effect-ey)
  private val effectTypeCache: Cache[Option[Type]] =
    new Cache[Option[Type]]()

  private val supertypeLevelCheckCache: Cache[Option[PureeLevel]] =
    new Cache[Option[PureeLevel]]()

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

  private def noSuspiciousEffects(stats: List[Tree]): Boolean = {
    stats.forall {
      case s if intended(s) =>
        true
      case t if t.isTerm =>
        getEffect(t).forall { eff =>
          reporter.warning(t.pos, s"Unused expression of type $eff")
          false
        }
      case _ =>
        true
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
          effectTypeCache.computeIfAbsent(
            tpe.safeToString,
            new function.Function[String, Option[Type]] {
              override def apply(t: String): Option[Type] = {
                scrutinize(a, tpe)
              }
            }
          )
        }
    }
  }

  private def scrutinize(a: Tree, tpe: Type): Option[Type] = {
    if (isOff(a, tpe)) {
      None
    } else if (isStrict(a, tpe) && !(tpe =:= UnitType)) {
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

  // probably weird and already solved somewhere
  private def fqnByClass(scr: RefTreeApi with SymTreeApi): Option[String] = {
    if (scr.qualifier.hasSymbolField) {
      val outerName: String = scr.qualifier.symbol.tpe.typeSymbol.fullName
      val symName: String = scr.symbol.nameString
      Some(s"$outerName.$symName")
    } else {
      None
    }
  }

  private def scrutineeFullNameWithTerm(a: Tree): Option[String] = {
    a match {
      case Apply(s: Select, _) => fqnByClass(s)
      case s: Select           => fqnByClass(s)
      case _                   => None
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

  private def isStrict(t: Tree, tp: Type): Boolean = {
    hasConfiguredLevel(t, tp, PureeLevel.Strict)
  }

  private def isOff(t: Tree, tp: Type): Boolean = {
    hasConfiguredLevel(t, tp, PureeLevel.Off)
  }

  private def hasConfiguredLevel(
      tree: Tree,
      treeType: Type,
      targetLevel: PureeLevel
  ): Boolean = {
    checkExactMatch(tree, targetLevel)
      .orElse(checkSupertypes(treeType, targetLevel))
      .getOrElse(puree.getConfig.global == targetLevel)
  }

  private def checkExactMatch(
      tree: Tree,
      targetLevel: PureeLevel
  ): Option[Boolean] = {
    scrutineeFullNameWithTerm(tree)
      .flatMap(puree.getConfig.detailed.get)
      .map(_ == targetLevel)
  }

  private def checkSupertypes(
      treeType: Type,
      targetLevel: PureeLevel
  ): Option[Boolean] = {
    supertypeLevelCheckCache
      .computeIfAbsent(
        treeType.typeSymbol.fullNameString,
        new function.Function[String, Option[PureeLevel]] {
          override def apply(t: String): Option[PureeLevel] = {
            configuredLevels.collectFirst {
              case (parent, (member, level))
                  if treeType.typeSymbol.isSubClass(parent.tpe.typeSymbol) &&
                    parent.info.member(encode(member)) != NoSymbol =>
                level
            }
          }
        }
      )
      .map(_ == targetLevel)
  }

  private def intended(t: Tree): Boolean = {
    def check(scrutinee: Annotatable[_]): Boolean =
      scrutinee.annotations.exists(_.tpe == typeOf[intended])

    if (t.isType && t.hasSymbolField) {
      check(t.symbol)
    } else if (t.isTerm) {
      check(t.tpe)
    } else if (t.isDef && t.hasSymbolField) {
      check(t.symbol)
    } else {
      false
    }
  }
}
