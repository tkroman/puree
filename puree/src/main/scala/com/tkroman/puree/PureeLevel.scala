package com.tkroman.puree

sealed trait PureeLevel
object PureeLevel {
  case object Off extends PureeLevel
  case object Effect extends PureeLevel
  case object Strict extends PureeLevel
}
