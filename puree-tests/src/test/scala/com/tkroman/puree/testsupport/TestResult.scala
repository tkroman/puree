package com.tkroman.puree.testsupport

import org.scalatest.{Assertion, Assertions}

sealed trait TestResult extends Assertions {
  def get: Assertion
}
object TestResult {
  case object Ok extends TestResult {
    override def get: Assertion =
      succeed
  }
  case class FailedPos(msg: String) extends TestResult {
    override def get: Assertion =
      fail(s"Failed to compile due to\n$msg")
  }
  case object FailedNeg extends TestResult {
    override def get: Assertion =
      fail(s"Compiled successfully but expected to fail")
  }
  case class Error(err: Exception) extends TestResult {
    override def get: Assertion =
      fail("Test run failed", err)
  }
}
