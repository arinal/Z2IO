package org.lamedh.z2io.core

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import java.util.concurrent.ScheduledExecutorService

sealed trait IO[+A] {

  import IO._

  def map[B](f: A => B): IO[B]         = Map(this, f)
  def flatMap[B](f: A => IO[B]): IO[B] = Flatmap(this, f)

  def repeat(n: Int): IO[A] = Repeat[A, Int](this, n, _ - 1, _ <= 1)
  def forever       : IO[A] = Repeat[A, Unit](this, (), identity, _ => false)

  def handleErr[B](h: Throwable => B): IO[B]         = IO.handleErrWith(this, h andThen IO.pure)
  def handleErrWith[B](h: Throwable => IO[B]): IO[B] = IO.handleErrWith(this, h)

  def *>[B](io: IO[B]): IO[B] = flatMap(_ => io)
}

object IO {

  type Callback[A]   = Either[Throwable, A] => Unit

  final case class Map[A, B](io: IO[B], f: B => A)         extends IO[A]
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

  def unsafeRunSync[A](io: IO[A]): A                      = IORunLoop.runSync(io)
  def unsafeRunAsync[A](io: IO[A], cb: Callback[A]): Unit = IORunLoop.runAsync(io, cb)

  def unsafeToFuture[A](io: IO[A]): Future[A] = {
    val p = Promise[A]
    IO.unsafeRunAsync[A](io, _.fold(p.failure, p.success))
    p.future
  }

  val never: IO[Nothing] = async[Nothing](_ => ())

  def shift(implicit ec: ExecutionContext): IO[Unit] =
    async[Unit] { cb =>
      ec.execute(() => cb(Right(())))
    }

  def sleep(duration: FiniteDuration)(implicit sched: ScheduledExecutorService, ec: ExecutionContext): IO[Unit] =
    async[Unit] { cb =>
      val wake: Runnable = () => cb(Right(()))
      sched.schedule(wake, duration.length, duration.unit)
    }

  def fork[A](io: IO[A])(implicit ec: ExecutionContext): IO[Unit] =
    async[Unit] { cb =>
      val spawnIO = (IO.shift *> io)
      unsafeRunAsync[A](spawnIO, (_: Either[Throwable, A]) => ())
      cb(Right(()))
    }

  def fromFuture[A](fut: => Future[A])(implicit ec: ExecutionContext): IO[A] =
    async[A] { cb =>
      fut.onComplete {
        case Success(v) => cb(Right(v))
        case Failure(t) => cb(Left(t))
      }
    }
}
