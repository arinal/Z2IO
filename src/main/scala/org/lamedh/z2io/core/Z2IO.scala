package org.lamedh.z2io.core

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.control.NonFatal
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object Z2IO {

  trait IO[+A] {

    import IO._

    def map[B](f: A => B): IO[B]         = Map(this, f)
    def flatMap[B](f: A => IO[B]): IO[B] = Bind(this, f)

    def *> [B](io: IO[B]): IO[B] = flatMap(_ => io)

    def handleError[B](h: Throwable => B): IO[B]         = IO.handleErrorWith(this, h andThen IO.pure)
    def handleErrorWith[B](h: Throwable => IO[B]): IO[B] = IO.handleErrorWith(this, h)

    def unsafeRunSync(): A                                     = IORunLoop.startSync(this)
    def unsafeRunAsync(cb: Either[Throwable, A] => Unit): Unit = IORunLoop.startAsync(this, cb)

    def unsafeToFuture(): Future[A] = {
      val p = Promise[A]
      unsafeRunAsync(_.fold(p.failure, p.success))
      p.future
    }
  }

  object IO {

    final case class Map[A, B](io: IO[A], f: A => B)                         extends IO[B]
    final case class Bind[A, B](io: IO[A], f: A => IO[B])                    extends IO[B]
    final case class Pure[A](a: A)                                           extends IO[A]
    final case class Delay[A](thunk: () => A)                                extends IO[A]
    final case class HandleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) extends IO[B]
    final case class RaiseError[T <: Throwable](t: T)                        extends IO[Nothing]
    final case class Async[A](k: (Either[Throwable, A] => Unit) => Unit)     extends IO[A]

    def apply[A](a: => A) = delay(a)

    def delay[A](thunk: => A)                                   = Delay(() => thunk)
    def pure[A](a: A)                                           = Pure(a)
    def raise[T <: Throwable](t: T)                             = RaiseError(t)
    def handleErrorWith[A, B](io: IO[A], h: Throwable => IO[B]) = HandleErrorWith(io, h)
    def async[A](k: (Either[Throwable, A] => Unit) => Unit)     = Async(k)

    def fromFuture[A](fut: => Future[A])(implicit ec: ExecutionContext): IO[A] = async { cb =>
      fut.onComplete {
        case Success(value) => cb(Right(value))
        case Failure(t)     => cb(Left(t))
      }
    }

    val never: IO[Nothing] = async(_ => ())
  }
}
