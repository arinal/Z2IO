package org.lamedh.z2io.core

import scala.annotation.tailrec
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.lamedh.z2io.core.Z2IO.IO
import scala.concurrent.Future

private object IORunLoop {

  import Z2IO._
  import IO._
  import Handler._

  def startSync[A](io: IO[A]): A                                       = loop(io, Nil)
  def startAsync[A](io: IO[A], cb: Either[Throwable, A] => Unit): Unit = loopAsync(io, Nil, cb)

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

  @tailrec
  private def loop[A](current: IO[Any], stack: List[Handler]): A =
    current match {
      case Map(io, f)             => loop(io, Happy(f andThen pure) :: stack)
      case Bind(io, f)            => loop(io, Happy(f) :: stack)
      case HandleErrorWith(io, h) => loop(io, Error(h) :: stack)
      case Delay(thunk) => {
        Try(thunk()) match {
          case Success(v) => loop(pure(v), stack)
          case Failure(t) => loop(raise(t), stack)
        }
      }
      case Pure(any) =>
        stack.dropWhile(_.isError) match {
          case Nil              => any.asInstanceOf[A]
          case Happy(f) :: tail => loop(f(any), tail)
          case _                => throw new AssertionError("Unreachable code")
        }
      case RaiseError(t) =>
        stack.dropWhile(!_.isError) match {
          case Nil              => throw t
          case Error(h) :: tail => loop(h(t), tail)
          case _                => throw new AssertionError("Unreachable code")
        }
      case Async(k) => suspendInAsync(current, k, stack.tail)
    }

  // TODO: implement this after threadpool management and sync toolset is done
  private def suspendInAsync[A](io: IO[A], k: (Either[Throwable, A] => Unit) => Unit, stack: List[Handler]) =
    throw new UnsupportedOperationException("Hit async boundary in a non-async operation")

  private def loopAsync[A](current: IO[Any], stack: List[Handler], cb: Either[Throwable, A] => Unit): Unit =
    current match {
      case Map(io, f)             => loopAsync(io, Happy(f andThen pure) :: stack, cb)
      case Bind(io, f)            => loopAsync(io, Happy(f) :: stack, cb)
      case HandleErrorWith(io, h) => loopAsync(io, Error(h) :: stack, cb)
      case Delay(thunk) => {
        Try(thunk()) match {
          case Success(v)                  => loopAsync(pure(v), stack, cb)
          case Failure(t) if stack.isEmpty => cb(Left(t))
          case Failure(t)                  => loopAsync(raise(t), stack, cb)
        }
      }
      case Pure(any) =>
        stack.dropWhile(_.isError) match {
          case Nil              => cb(Right(any.asInstanceOf[A]))
          case Happy(f) :: tail => loopAsync(f(any), tail, cb)
          case _                => throw new AssertionError("Unreachable code")
        }
      case RaiseError(t) =>
        stack.dropWhile(!_.isError) match {
          case Nil              => throw t
          case Error(h) :: tail => loopAsync(h(t), tail, cb)
          case _                => throw new AssertionError("Unreachable code")
        }
      case Async(k) =>
        val rest = { res: Either[Throwable, Any] =>
          loopAsync(res.fold(raise, pure), stack, cb)
        }
        k(rest)
    }
}
