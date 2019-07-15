object BuilderIntended {
  import com.tkroman.puree.annotation.intended
  def f(): List[Int] = {
    val buf = List.newBuilder[Int]
    (buf += 1): @intended
    buf.result()
  }
}
