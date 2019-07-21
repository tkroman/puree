// string <: comparable[String]
object StringShouldCompileEvenThoughComparable {
  def g(): String = ""
  def f(): Unit = {
    g()
    ()
  }
}
