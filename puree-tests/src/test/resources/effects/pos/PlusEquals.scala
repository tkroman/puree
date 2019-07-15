object PlusEquals {
  class X[A] {
    var x = 0
    def +=(i: Int): X[A] = {
      x += i
      this
    }
  }

  def f(): Unit = {
    val x = new X[Int]
    x += 1
    ()
  }
}
