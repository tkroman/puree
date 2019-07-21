object GrowablePlusEqualsIsOkSinceOffInSettings {
  import scala.collection.mutable
  def f(): mutable.BitSet = {
    val buf = new mutable.BitSet()
    buf += 1
    buf.result()
  }
}
