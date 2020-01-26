package org.lamedh.trio.core

import scala.annotation.tailrec
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.lamedh.trio.core.Trio.IO
import Trio.Handler

private object IORunLoop {

  import Handler._
  import Trio._
  import IO._

  def startSync[A](io: IO[A]): A                                       = loop(io, Nil)
  def startAsync[A](io: IO[A], cb: Either[Throwable, A] => Unit): Unit = loop(io, Nil, cb)

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

  private def suspendInAsync[A](io: IO[A], k: (Either[Throwable, A] => Unit) => Unit, stack: List[Handler]) = ???

  private def loop[A](current: IO[Any], stack: List[Handler], cb: Either[Throwable, A] => Unit): Unit =
    current match {
      case Map(io, f)             => loop(io, Happy(f andThen pure) :: stack, cb)
      case Bind(io, f)            => loop(io, Happy(f) :: stack, cb)
      case HandleErrorWith(io, h) => loop(io, Error(h) :: stack, cb)
      case Delay(thunk) => {
        Try(thunk()) match {
          case Success(v) => loop(pure(v), stack, cb)
          case Failure(t) => loop(raise(t), stack, cb)
        }
      }
      case Pure(any) =>
        stack.dropWhile(_.isError) match {
          case Nil              => cb(Right(any.asInstanceOf[A]))
          case Happy(f) :: tail => loop(f(any), tail, cb)
          case _                => throw new AssertionError("Unreachable code")
        }
      case RaiseError(t) =>
        stack.dropWhile(!_.isError) match {
          case Nil              => throw t
          case Error(h) :: tail => loop(h(t), tail, cb)
          case _                => throw new AssertionError("Unreachable code")
        }
      case Async(k) =>
        val rest = { res: Either[Throwable, Any] =>
          loop(res.fold(raise, pure), stack, cb)
        }
        k(rest)
    }
}
