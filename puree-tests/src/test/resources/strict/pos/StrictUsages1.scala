object StrictUsages1 {
  def o(): Unit = ()

  def f(): Int = {
    o()
    1
  }
}
