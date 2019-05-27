object Nested {
  def f(): Int = {
    Option(1)
      .map { _ =>
        3
      }
      .getOrElse(0)
  }

  def g(): Int = {
    Option(1)
      .flatMap { _ =>
        Option(3)
      }
      .getOrElse(0)
  }
}
