object BuilderNeg {
  def f(): List[Int] = {
    val buf = List.newBuilder[Int]
    buf += 1
    buf.result()
  }
}
