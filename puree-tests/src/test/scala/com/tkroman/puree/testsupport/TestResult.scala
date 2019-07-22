package com.tkroman.puree.testsupport

import org.scalactic.source.Position
import org.scalatest.{Assertion, Assertions}

sealed trait TestResult extends Assertions {
  def get: Assertion
}
object TestResult {
  case object Ok extends TestResult {
    override def get: Assertion =
      succeed
  }
  case class FailedPos(msg: String)(pos: Position) extends TestResult {
    override def get: Assertion =
      fail(s"Failed to compile:\n$msg")(pos)
  }
  case class FailedNeg(pos: Position) extends TestResult {
    override def get: Assertion =
      fail(s"Compiled successfully but expected to fail")(pos)
  }
  case class Error(err: Exception)(pos: Position) extends TestResult {
    override def get: Assertion =
      fail("Test run failed", err)(pos)
  }
}
