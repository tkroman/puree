object PcTest {
  class Bar[A, B]
  class Baz[A, B](val l: Bar[A, B])
  def foo[A](): Baz[A, Int] = new Baz(new Bar)
  class X

  implicit class Foo[A](_l: Bar[A, X]) extends Baz[A, X](_l) {
    def field: Baz[A, Int] = foo()
  }
}
