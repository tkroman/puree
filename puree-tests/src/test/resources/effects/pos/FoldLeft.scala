object FoldLeft {
  def distinct1[A](xs: List[A]): List[A] = {
    import com.tkroman.puree.annotation.intended
    import scala.collection.immutable

    val buf = List.newBuilder[A]
    xs.tail.foldLeft(immutable.HashSet(xs.head: A)) { (seen, x) =>
      if (seen(x)) seen
      else {
        buf += x
        seen + x
      }
    }: @intended
    buf.result()
  }

  def distinct2[A](xs: List[A]): List[A] = {
    import com.tkroman.puree.annotation.intended
    import scala.collection.immutable

    val buf = List.newBuilder[A]
    xs.tail.foldLeft(immutable.HashSet(xs.head: A)) { (seen, x) =>
      if (seen(x)) seen
      else {
        buf += x
        seen + x
      }
    }: @intended("foldLeft only used for traversal w/ accumulated set")
    buf.result()
  }

  def distinct3[A](xs: List[A]): List[A] = {
    import scala.collection.immutable
    val buf =
      xs.tail
        .foldLeft((List.newBuilder[A], immutable.HashSet(xs.head: A))) {
          case ((buf, seen), x) =>
            if (seen(x)) (buf, seen)
            else {
              (buf += x, seen + x)
            }
        }
        ._1
    buf.result()
  }
}
