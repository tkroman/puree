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
            case dd: Block if dd.stats.nonEmpty =>
              dd.stats.foreach {
                // ????!!!
                case a: Apply
                    if Option(a.tpe).forall(_.typeSymbol.typeParams.nonEmpty) =>
                  reporter.warning(a.pos, "Unused effect")
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
  }
}
