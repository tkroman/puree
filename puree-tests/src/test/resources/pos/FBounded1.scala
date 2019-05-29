object FBounded1 {
  trait F[X <: F[X]]
  class Foo extends F[Foo]
  def g() = {
    new Foo
  }
  def f(): Unit = {
    g()
    ()
  }
}
