package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent._

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.Semaphore

private object IORunLoop {

  import IO._

  def runSync[A](io: IO[A]): A                            = loop(io, Nil, None).get
  def runAsync[A](io: IO[A], finished: Callback[A]): Unit = loop(io, Nil, Some(finished))

  sealed trait Bind { def isKo: Boolean }
  final case class Ok(f: Any => IO[Any])       extends Bind { def isKo = false }
  final case class Ko(f: Throwable => IO[Any]) extends Bind { def isKo = true }

  @tailrec
  private def loop[A](current: IO[Any], stack: List[Bind], finishCb: Option[Callback[A]]): Option[A] =
    current match {
      case Map(io, f)       => loop(io, Ok(f andThen pure) :: stack, finishCb)
      case Flatmap(io, f)   => loop(io, Ok(f) :: stack, finishCb)
      case HandleErr(io, f) => loop(io, Ko(f) :: stack, finishCb)
      case Delay(f) => {
        Try(f()) match {
          case Success(v)                  => loop(pure(v), stack, finishCb)
          case Failure(t) if stack.isEmpty => finishCb.foreach(_(Left(t))); None
          case Failure(t)                  => loop(raise(t), stack, finishCb)
        }
      }
      case Pure(v) =>
        stack.dropWhile(_.isKo) match {
          case Nil if finishCb.isDefined => finishCb.foreach(_(Right(v.asInstanceOf[A]))); None
          case Nil                       => Some(v.asInstanceOf[A])
          case Ok(f) :: tail             => loop(f(v), tail, finishCb)
          case _                         => throw new AssertionError("Unreachable code")
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
      case Async(f) =>
        finishCb match {
          case None     => suspendInAsync[A](current, stack)
          case Some(cb) => f(resumeLoop(cb, stack)); None
        }
    }

  private def resumeLoop[A](cb: Callback[A], stack: List[Bind]): Either[Throwable, Any] => Unit =
    res => loop(res.fold(raise, pure), stack, Some(cb))

  private def suspendInAsync[A](io: IO[Any], stack: List[Bind]) = {
    val sem = new Semaphore(0)
    var result: Either[Throwable, Any] = null

    val finishCb = { p: Either[Throwable, Any] =>
      result = p
      sem.release()
    }
    loop(io, stack, Some(finishCb))
    blocking(sem.acquire())
    Some(result.right.get.asInstanceOf[A])
  }
}
