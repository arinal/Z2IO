package org.lamedh.z2io.core

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import scala.annotation.tailrec

import java.util.concurrent.ScheduledExecutorService

sealed trait IO[+A] {

  import IO._

  def map[B](f: A => B): IO[B]         = Flatmap(this, f andThen pure)
  def flatMap[B](f: A => IO[B]): IO[B] = Flatmap(this, f)

  def repeat(n: Int): IO[A] = Repeat[A, Int](this, n, _ - 1, _ <= 1)
  def forever       : IO[A] = Repeat[A, Unit](this, (), identity, _ => false)

  def fork(implicit ec: ExecutionContext): IO[Fiber[A]] = IO.fork(this)(ec)

  def handleErr[B](h: Throwable => B): IO[B]         = IO.handleErrWith(this, h andThen IO.pure)
  def handleErrWith[B](h: Throwable => IO[B]): IO[B] = IO.handleErrWith(this, h)

  def *>[B](io: IO[B]): IO[B] = flatMap(_ => io)
}

object IO {

  type Callback[-A] = Either[Throwable, A] => Unit

  final case class Flatmap[A, B](io: IO[B], f: B => IO[A]) extends IO[A]
  final case class Pure[A](a: A)                           extends IO[A]
  final case class Delay[A](f: () => A)                    extends IO[A]
  final case class Async[A](f: Callback[A] => Unit)        extends IO[A]

  final case class Repeat[A, B](io: IO[A], init: B, ft: B => B, fp: B => Boolean) extends IO[A]

  final case class HandleErr[A, B](io: IO[A], h: Throwable => IO[B]) extends IO[B]
  final case class Error[T <: Throwable](t: T) extends IO[Nothing]

  def unit                                                  = pure(())
  def pure[A](a: A)                                         = Pure(a)
  def apply[A](a: => A)                                     = Delay(() => a)
  def raise[T <: Throwable](t: T)                           = Error(t)
  def handleErrWith[A, B](io: IO[A], h: Throwable => IO[B]) = HandleErr(io, h)
  def async[A](f: Callback[A] => Unit): IO[A]               = Async(f)

  val never: IO[Nothing] = async[Nothing](_ => ())

  def shift(implicit ec: ExecutionContext): IO[Unit] =
    async[Unit] { cb =>
      ec.execute(() => cb(Right(())))
    }

  def sleep(duration: FiniteDuration)(implicit sched: ScheduledExecutorService): IO[Unit] =
    async[Unit] { cb =>
      val wake: Runnable = () => cb(Right(()))
      sched.schedule(wake, duration.length, duration.unit)
    }

  def fork[A](io: IO[A])(implicit ec: ExecutionContext): IO[Fiber[A]] =
    async[Fiber[A]] { cb =>
      val spawnIO = (IO.shift *> io)
      val fiber = Fiber(spawnIO)
      fiber.unsafeRun(_ => ())
      cb(Right(fiber))
    }

  def fromFuture[A](fut: => Future[A])(implicit ec: ExecutionContext): IO[A] =
    async[A] { cb =>
      fut.onComplete {
        case Success(v) => cb(Right(v))
        case Failure(t) => cb(Left(t))
      }
    }
}
