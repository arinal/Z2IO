package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO

import scala.util.{Failure, Success, Try}
import scala.annotation.tailrec

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Semaphore

object Runloop {

  import IO._

  sealed trait Bind { def isKo: Boolean }
  final case class Ok(f: Any => IO[Any])       extends Bind { def isKo = false }
  final case class Ko(f: Throwable => IO[Any]) extends Bind { def isKo = true }

  @tailrec
  def loop[A](current: IO[Any], stack: List[Bind], finishCb: IO.Callback[A]): Unit =
    current match {
      case Flatmap(io, f)   => loop(io, Ok(f) :: stack, finishCb)
      case HandleErr(io, f) => loop(io, Ko(f) :: stack, finishCb)
      case Delay(f) =>
        Try(f()) match {
          case Success(v)                  => loop(pure(v), stack, finishCb)
          case Failure(t) if stack.isEmpty => finishCb(Left(t))
          case Failure(t)                  => loop(raise(t), stack, finishCb)
        }
      case Pure(v) =>
        stack.dropWhile(_.isKo) match {
          case Nil           => finishCb(Right(v.asInstanceOf[A]))
          case Ok(f) :: tail => loop(f(v), tail, finishCb)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Repeat(io, flag, ft, fp) =>
        val nextIO =
          if (fp(flag)) io
          else Flatmap(io, (_: Any) => Repeat(io, ft(flag), ft, fp))
        loop(nextIO, stack, finishCb)
      case Error(t) =>
        stack.dropWhile(!_.isKo) match {
          case Nil           => throw t
          case Ko(h) :: tail => loop(h(t), tail, finishCb)
          case _             => throw new AssertionError("Unreachable code")
        }
      case Async(f) => f(resume(finishCb, stack))
    }

  private def resume[A](cb: Callback[A], stack: List[Bind]): Either[Throwable, Any] => Unit =
    res => loop(res.fold(raise, pure), stack, cb)
}
