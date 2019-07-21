package com.tkroman.puree

sealed trait PureeLevel {
  def isOff: Boolean = false
}
object PureeLevel {
  case object Off extends PureeLevel {
    override def isOff: Boolean = true
  }
  case object Effect extends PureeLevel
  case object Strict extends PureeLevel
}
