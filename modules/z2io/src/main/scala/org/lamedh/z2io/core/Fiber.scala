package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO

import scala.concurrent.Future
import scala.concurrent._

import java.util.concurrent.Semaphore

trait Fiber[A] {
  def join: IO[A]

  def unsafeRunSync(): A
  def unsafeRun(cb: IO.Callback[A]): Unit
}

private object Fiber {

  import IO._

  def apply[A](io: IO[A]): Fiber[A] = new DefaultFiber(io)

  def unsafeRunSync[A](io: IO[A]): A                      = Fiber(io).unsafeRunSync()
  def unsafeRunAsync[A](io: IO[A], cb: Callback[A]): Unit = Fiber(io).unsafeRun(cb)

  def unsafeToFuture[A](io: IO[A]): Future[A] = {
    val p = Promise[A]
    unsafeRunAsync[A](io, _.fold(p.failure, p.success))
    p.future
  }

  class DefaultFiber[A](io: IO[A]) extends Fiber[A] {

    var result: Option[Either[Throwable, A]] = None
    var joinerCb: Option[Callback[A]]        = None

    def join: IO[A] =
      result match {
        case Some(Right(res)) => pure(res)
        case Some(Left(err))  => raise(err)
        case None =>
          async[A] { cb =>
            joinerCb = Some(cb)
          }
      }

    def unsafeRun(cb: IO.Callback[A]): Unit = {
      val newCb = { res: Either[Throwable, A] =>
        result = Some(res)
        joinerCb.foreach(_(res))
        cb(res)
      }
      Runloop.loop(io, Nil, newCb)
    }

    def unsafeRunSync(): A = {
      val sem                                  = new Semaphore(0)
      var result: Option[Either[Throwable, A]] = None

      val finishCb = { res: Either[Throwable, A] =>
        result = Some(res)
        blocking(sem.release())
      }
      unsafeRun(finishCb)
      result match {
        case Some(Right(res)) => res
        case Some(Left(err))  => throw err
        case None =>
          blocking(sem.acquire())
          result.get.fold(throw _, identity)
      }
    }
  }
}
