package org.lamedh.trio.core

import scala.util.control.NonFatal
import scala.annotation.tailrec
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object Trio {

  trait IO[+A] {
    def map[B](f: A => B): IO[B]         = Map(this, f)
    def flatMap[B](f: A => IO[B]): IO[B] = Bind(this, f)

    def handleError[B](h: Throwable => B): IO[B]         = IO.handleErrorWith(this, h andThen IO.pure)
    def handleErrorWith[B](h: Throwable => IO[B]): IO[B] = IO.handleErrorWith(this, h)

    def unsafeRunSync(): Unit = IO.unsafeRunSync(this)
    def unsafeRunAsync(cb: Either[Throwable, A] => Unit): Unit = IO.unsafeRunAsync(this, cb)
  }

  final case class Map[A, B](io: IO[A], f: A => B)                         extends IO[B]
  final case class Bind[A, B](io: IO[A], f: A => IO[B])                    extends IO[B]
  final case class Pure[A](a: A)                                           extends IO[A]
  final case class Delay[A](thunk: () => A)                                extends IO[A]
  final case class HandleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) extends IO[B]
  final case class RaiseError[T <: Throwable](t: T)                        extends IO[Nothing]
  final case class Async[A](k: (Either[Throwable, A] => Unit) => Unit)     extends IO[A]

  object IO {

    def apply[A](a: => A)                                       = delay(a)

    def delay[A](thunk: => A)                                   = Delay(() => thunk)
    def pure[A](a: A)                                           = Pure(a)
    def raise[T <: Throwable](t: T)                             = RaiseError(t)
    def handleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) = HandleErrorWith(io, h)
    def async[A](k: (Either[Throwable, A] => Unit) => Unit)     = Async(k)

    def unsafeRunSync[A](io: IO[A]): Unit                                    = IORunLoop.startSync(io)
    def unsafeRunAsync[A](io: IO[A], cb: Either[Throwable, A] => Unit): Unit = IORunLoop.startAsync(io, cb)
  }
}
