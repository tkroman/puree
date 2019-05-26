object PcTest {
  // if -Ywarn-value-discard is enabled, this will be caught
  // we don't make attempts at this
  def f(): Unit = {
    Option(1)
  }
}
