object UnusedOptionWithIgnore {
  import com.tkroman.puree.annotation.intended
  def f(): Int = {
    (Option(1): @intended)
    1
  }
}
