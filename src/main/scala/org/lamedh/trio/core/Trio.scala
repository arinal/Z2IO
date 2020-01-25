package org.lamedh.trio.core

object Trio {

  trait IO[+A] {
    def map[B](f: A => B): IO[B] = Map(this, f)
    def flatMap[B](f: A => IO[B]): IO[B] = Bind(this, f)
    def unsafeRunSync(): A = IO.unsafeRunSync(this)
  }

  final case class Map[A, B](io: IO[A], f: A => B) extends IO[B]
  final case class Bind[A, B](io: IO[A], f: A => IO[B]) extends IO[B]
  final case class Pure[A](a: A) extends IO[A]
  final case class Delay[A](thunk: () => A) extends IO[A]

  object IO {

    def delay[A](thunk: => A) = Delay(() => thunk)
    def pure[A](a: A) = Pure(a)
    def apply[A](a: => A) = delay(a)

    def unsafeRunSync[A](io: IO[A]): A = {
      def loop(current: IO[Any], stack: List[Any => IO[Any]]): A = {
        current match {
          case Map(io, f)   => loop(io, f.andThen(pure) :: stack)
          case Bind(io, f)  => loop(io, f :: stack)
          case Delay(thunk) => loop(pure(thunk()), stack)
          case Pure(any) =>
            stack match {
              case Nil       => any.asInstanceOf[A]
              case f :: tail => loop(f(any), tail)
            }
        }
      }
      loop(io, Nil)
    }
  }
}
