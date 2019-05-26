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
      val tt: global.Traverser = new Traverser {
        override def traverse(tree: Tree): Unit = {
          tree match {
            case dd: ValOrDefDef if dd.rhs.children.nonEmpty =>
              val exprs: List[global.Tree] = dd.rhs.children
              if (exprs.lengthCompare(1) > 0) {
                val scr =
                  if (exprs.last.hasExistingSymbol && !exprs.last.symbol.isSynthetic) {
                    exprs.dropRight(1)
                  } else {
                    exprs.dropRight(2)
                  }

                scr.foreach {
                  case a: Apply if a.fun.symbol.info.isHigherKinded =>
                    global.reporter.warning(a.pos, "Unused effect")
                  case _ =>
                  // noop
                }
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
