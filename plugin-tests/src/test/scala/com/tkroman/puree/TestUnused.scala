package com.tkroman.puree

// Test for issue #12
object TestUnused extends App {
  def f(): Int = {
    Option(1)
    1
  }

  def g(): Int = {
    Option(1)
    1
  }

  def h(): Unit = {
    Option(1)
  }
}
