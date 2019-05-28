package com.tkroman.puree

import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}

class Puree(val global: Global) extends Plugin {
  override val name = "puree"
  override val description = "Warn about unused effects"
  override val components = List(
    new UnusedEffectDetector(this, global)
  )
}

class UnusedEffectDetector(plugin: Puree, val global: Global)
    extends PluginComponent {
  import global._

  override val runsAfter = List("typer")
  override val runsBefore = List("patmat")
  override val phaseName = "puree-checker"

  override def newPhase(prev: Phase): Phase = new StdPhase(prev) {
    override def apply(unit: CompilationUnit): Unit = {
      val tt: Traverser = new Traverser {
        override def traverse(tree: Tree): Unit = {
          tree match {
            case Block(stats, _) =>
              stats.foreach {
                case a: Apply =>
                  isEffect(a).foreach { eff =>
                    reporter.warning(
                      a.pos,
                      s"Unused effectful function call of type $eff"
                    )
                  }
                case a: Select =>
                  isEffect(a).foreach { eff =>
                    reporter.warning(
                      a.pos,
                      s"Unused effectful member reference of type $eff"
                    )
                  }

                case _ =>
                // noop
              }
            case _ =>
            // noop
          }
          super.traverse(tree)
        }
      }
      tt.traverse(unit.body)
    }

    private def isEffect(a: Tree): Option[Type] = {
      a match {
        case Apply(s @ Select(Super(_, _), _), _) if s.symbol.isConstructor =>
          // in constructors, calling super.<init>
          // when super is an F[_, _*] is seen as an
          // unassigned effectful value :(
          None
        case _ =>
          Option(a.tpe).flatMap { tpe =>
            tpe.baseTypeSeq.toList.find(_.typeSymbol.typeParams.nonEmpty)
          }
      }
    }
  }
}
