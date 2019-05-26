package com.tkroman.puree

import org.ensime.pcplod._
import org.scalatest.funsuite.AnyFunSuite

class PresentationCompiler extends AnyFunSuite {
  test("used") {
    withMrPlod("UsedOption.scala") { pc =>
      assert(pc.messages.isEmpty)
    }
  }

  test("unused") {
    withMrPlod("UnusedOption.scala") { pc =>
      assert(pc.messages.size == 1)
    }
  }

  test("unused neg") {
    withMrPlod("UnusedOptionNeg.scala") { pc =>
      assert(pc.messages.isEmpty)
    }
  }
}
