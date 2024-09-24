package org.lamedh

import org.lamedh.z2io.core.IO
import org.lamedh.z2io.core.IOApp
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.Executors

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    general

  def general = {
    import scala.concurrent.duration._
    import IOApp._

    def log(msg: String)      = println(s"$msg  On thread: ${Thread.currentThread().getName}")
    def logAsync(msg: String) = Future(log(msg))

    def ticking(res: Int) = (IO(log("Tick")) *> IO.sleep(1.second)).repeat(3) *> IO.pure(res)

    for {
      _ <- IO.pure(5)

      _ <- IO(throw new Exception("Boom"))
            .handleErr(_ => 5)

      _ <- IO(log("#1 :)"))
      _ <- IO.async[Unit] { cb =>
            logAsync("#2 :)").onComplete {
              case Success(v) => cb(Right(v))
              case Failure(t) => cb(Left(t))
            }
          }
      _ <- IO.fromFuture(logAsync("#3 :)"))

      _ <- IO(log("Before shift"))
      _ <- IO.shift
      _ <- IO(log("After shift"))

      _ <- IO(log("Wait 1s"))
      _ <- IO.sleep(1.second)
      _ <- IO(log("Waited 1s"))

      f1 <- IO.fork(ticking(1))
      f2 <- IO.fork(ticking(2))
      f3 <- IO.fork(ticking(3))

      n1 <- f1.join
      n2 <- f2.join
      n3 <- f3.join

      _ <- IO(log(s"Result: $n1, $n2, $n3"))

      _ <- IO(log("executing IO.never"))
      _ <- IO.never
    } yield ()
  }
}
