package org.lamedh.z2io.core

import org.lamedh.z2io.core.Z2IO.IO
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent._
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Semaphore

private object IORunLoop {

  import Z2IO._
  import IO._

  def startSync[A](io: IO[A]): A                                       = loop(io, Nil)
  def startAsync[A](io: IO[A], cb: Either[Throwable, A] => Unit): Unit = loopAsync(io, Nil, cb)

  sealed trait Bind { def isKo: Boolean }
  final case class Ok(f: Any => IO[Any])       extends Bind { def isKo = false }
  final case class Ko(f: Throwable => IO[Any]) extends Bind { def isKo = true }

  @tailrec
  private def loop[A](current: IO[Any], stack: List[Bind]): A =
    current match {
      case Map(io, f)          => loop(io, Ok(f andThen pure) :: stack)
      case Flatmap(io, f)      => loop(io, Ok(f) :: stack)
      case FlatmapError(io, f) => loop(io, Ko(f) :: stack)
      case Delay(f) => {
        Try(f()) match {
          case Success(v) => loop(pure(v), stack)
          case Failure(t) => loop(raise(t), stack)
        }
      }
      case Pure(any) =>
        stack.dropWhile(_.isKo) match {
          case Nil           => any.asInstanceOf[A]
          case Ok(f) :: tail => loop(f(any), tail)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Error(t) =>
        stack.dropWhile(!_.isKo) match {
          case Nil           => throw t
          case Ko(h) :: tail => loop(h(t), tail)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Async(_) => suspendInAsync[A](current, stack)
    }

  private def suspendInAsync[A](io: IO[Any], stack: List[Bind]) = {
    val sem                         = new Semaphore(0)
    var ref: Either[Throwable, Any] = null

    val cb = { p: Either[Throwable, Any] =>
      ref = p
      sem.release()
    }
    loopAsync(io, stack, cb)
    blocking(sem.acquire())
    ref.right.get.asInstanceOf[A]
  }

  private def loopAsync[A](current: IO[Any], stack: List[Bind], cb: Either[Throwable, A] => Unit): Unit =
    current match {
      case Map(io, f)          => loopAsync(io, Ok(f andThen pure) :: stack, cb)
      case Flatmap(io, f)      => loopAsync(io, Ok(f) :: stack, cb)
      case FlatmapError(io, h) => loopAsync(io, Ko(h) :: stack, cb)
      case Delay(f) => {
        Try(f) match {
          case Success(v)                  => loopAsync(pure(v), stack, cb)
          case Failure(t) if stack.isEmpty => cb(Left(t))
          case Failure(t)                  => loopAsync(raise(t), stack, cb)
        }
      }
      case Pure(any) =>
        stack.dropWhile(_.isKo) match {
          case Nil           => cb(Right(any.asInstanceOf[A]))
          case Ok(f) :: tail => loopAsync(f(any), tail, cb)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Error(t) =>
        stack.dropWhile(!_.isKo) match {
          case Nil           => cb(Left(t))
          case Ko(h) :: tail => loopAsync(h(t), tail, cb)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Async(k) =>
        val rest = { res: Either[Throwable, Any] =>
          loopAsync(res.fold(raise, pure), stack, cb)
        }
        k(rest)
    }
}
