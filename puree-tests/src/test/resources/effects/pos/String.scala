// string <: comparable[String]
object String {
  def g(): String = ""
  def f(): Unit = {
    g()
    ()
  }
}
