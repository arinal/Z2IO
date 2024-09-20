package org.lamedh

import org.lamedh.z2io.core.IO
import org.lamedh.z2io.core.IOApp
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import java.util.concurrent.Executors

object Main extends IOApp {

  def run(args: Array[String]): IO[Unit] = {

    import scala.concurrent.duration._
    import IOApp._

    def log(msg: String)      = println(s"$msg  On thread: ${Thread.currentThread().getName}")
    def logAsync(msg: String) = Future(log(msg))

    def ticking() = while (true) {
      log("Tick")
      Thread.sleep(1000)
    }

    for {
      _ <- IO.pure(5) // 5 is evaluated eagerly, don't use it for wrapping side effects

      _ <- IO(throw new Exception("Boom"))
            .handleErr(_ => 5) // error is handled and IO with value 5 is returned

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

      _ <- IO.fork(IO(ticking()))
      _ <- IO.fork(IO(ticking()))
      _ <- IO.fork(IO(ticking()))

      _ <- IO.never // this will never ever completed
    } yield ()
  }
}
