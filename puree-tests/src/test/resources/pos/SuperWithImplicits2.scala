object SuperWithImplicits1 {
  class EvA[A]
  object EvA {
    implicit val evLong = new EvA[Long]
  }

  class EvB[A]
  object EvB {
    implicit val evLong = new EvB[Long]
  }

  trait F[A]
  class G[A: EvA: EvB](val f: A => String)(val g: A => String) extends F[A]
  object X extends G[Long](_.toString)(_.toString)
}
