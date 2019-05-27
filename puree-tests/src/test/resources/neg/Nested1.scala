object Nested1 {
  def f(): Int = {
    Option(1)
      .map { _ =>
        Option(2)
        3
      }
      .getOrElse(0)
  }

  def g(): Int = {
    Option(1)
      .flatMap { _ =>
        scala.util.Try(2)
        Option(3)
      }
      .getOrElse(0)
  }
}
