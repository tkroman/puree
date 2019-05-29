package com.tkroman.puree

import scala.annotation.tailrec
import scala.tools.nsc.plugins.{Plugin, PluginComponent}
import scala.tools.nsc.{Global, Phase}
import com.tkroman.puree.annotation.intended

// TODO detection:
// - exclude things like Comparable[String], Comparable[ByteBuffer], ... (basically every F[A <: F[A]]?)
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
            case Template(_, _, body) =>
              findEffects(body)
            case Block(stats, _) =>
              findEffects(stats)
            case _ => // noop
          }
          super.traverse(tree)
        }
      }
      tt.traverse(unit.body)
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

  private def dbg(t: Tree): Unit = {
    println(t.pos.source.file.name + " " + showRaw(t))
  }

  private def getEffect(a: Tree): Option[Type] = {
    a match {
      case _ if intended(a) =>
        // respect `intended`
        None
      case _ if isSuperConstructorCall(a) =>
        // in constructors, calling super.<init>
        // when super is an F[_, _*] is seen as an
        // unassigned effectful value :(
        None
      case Apply(Select(_, op), _) if op.isOperatorName =>
        // ignore operators
        None
      case _ if a.tpe.baseTypeSeq.exists { bt =>
            val info: global.Type = bt.typeSymbol.info
            info.typeParams.nonEmpty && info.typeParams.forall(_.isFBounded)
          } =>
        None
      case _ =>
        Option(a.tpe).flatMap { tpe =>
          tpe.baseTypeSeq.toList.find(_.typeSymbol.typeParams.nonEmpty)
        }
    }
  }

  private def findEffects(stats: List[Tree]): Unit = {
    stats.foreach {
      case a: Apply =>
        getEffect(a).foreach { eff =>
          reporter.warning(
            a.pos,
            s"Unused effectful function call of type $eff"
          )
        }
      case a: Select =>
        getEffect(a).foreach { eff =>
          reporter.warning(
            a.pos,
            s"Unused effectful member reference of type $eff"
          )
        }

      case _ =>
      // noop
    }
  }

  private def intended(a: global.Tree): Boolean = {
    a.symbol.annotations
      .exists(_.tpe == typeOf[intended])
  }
}
