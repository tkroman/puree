object FBounded0 {
  trait G[A <: G[A]]
  class X extends G[X]
  def f(): X = new X

  def g(): Int = {
    f()
    1
  }
}
