object StrictUsages0 {
  def o(): Int = 1

  def f(): Int = {
    o()
    1
  }
}
