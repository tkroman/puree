object SuperWithImplicits1 {
  class Ev[A]
  object Ev {
    implicit val evLong = new Ev[Long]
  }
  trait F[A]
  class G[A: Ev](val f: A => String) extends F[A]
  object X extends G[Long](_.toString)
}
