package org.lamedh.trio.core

import scala.util.control.NonFatal

object Trio {

  trait IO[+A] {
    def map[B](f: A => B): IO[B]         = Map(this, f)
    def flatMap[B](f: A => IO[B]): IO[B] = Bind(this, f)

    def handleError[B](h: Throwable => B): IO[B]         = IO.handleErrorWith(this, h andThen IO.pure)
    def handleErrorWith[B](h: Throwable => IO[B]): IO[B] = IO.handleErrorWith(this, h)

    def unsafeRunSync(): A = IO.unsafeRunSync(this)
  }

  trait Handler {
    def isError: Boolean
  }

  object Handler {
    final case class Happy(f: Any => IO[Any]) extends Handler {
      override def isError: Boolean = false
    }
    final case class Error(f: Throwable => IO[Any]) extends Handler {
      override def isError: Boolean = true
    }
  }

  final case class Map[A, B](io: IO[A], f: A => B) extends IO[B]
  final case class Bind[A, B](io: IO[A], f: A => IO[B]) extends IO[B]
  final case class Pure[A](a: A) extends IO[A]
  final case class Delay[A](thunk: () => A) extends IO[A]
  final case class HandleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) extends IO[B]
  final case class RaiseError[T <: Throwable](t: T) extends IO[T]

  object IO {

    def delay[A](thunk: => A)                                   = Delay(() => thunk)
    def pure[A](a: A)                                           = Pure(a)
    def raise[T <: Throwable](t: T)                             = RaiseError(t)
    def handleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) = HandleErrorWith(io, h)

    def apply[A](a: => A) = delay(a)

    def unsafeRunSync[A](io: IO[A]): A = {
      import Handler._
      def loop(current: IO[Any], stack: List[Handler]): A =
        current match {
          case Map(io, f)             => loop(io, Happy(f andThen pure) :: stack)
          case Bind(io, f)            => loop(io, Happy(f) :: stack)
          case HandleErrorWith(io, h) => loop(io, Error(h) :: stack)
          case Delay(thunk) => {
            try {
              val value = thunk()
              loop(pure(value), stack)
            } catch {
              case NonFatal(t) => loop(raise(t), stack)
            }
          }
          case Pure(any) =>
            stack.dropWhile(_.isError) match {
              case Nil              => any.asInstanceOf[A]
              case Happy(f) :: tail => loop(f(any), tail)
              case _                => throw new Exception("Unexpected Error handler")
            }
          case RaiseError(t) =>
            stack.dropWhile(!_.isError) match {
              case Nil              => throw t
              case Error(h) :: tail => loop(h(t), tail)
              case _                => throw new Exception("Unexpected happy handler")
            }
        }
      loop(io, Nil)
    }
  }
}
