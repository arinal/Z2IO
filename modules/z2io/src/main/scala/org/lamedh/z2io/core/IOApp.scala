package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

trait IOApp {

  def run(args: Array[String]): IO[Unit]

  final def main(args: Array[String]) =
    IOApp.unsafeRunSync(args, run)
}

object IOApp {

  trait Simple {
    def run: IO[Unit]

    final def main(args: Array[String]) =
      unsafeRunSync(args, _ => run)
  }

  implicit lazy val sleepers: ScheduledExecutorService = Executors.newScheduledThreadPool(12)
  implicit lazy val ec: ExecutionContext               = ExecutionContext.fromExecutorService(sleepers)

  private def unsafeRunSync(args: Array[String], run: Array[String] => IO[Unit]): Unit =
    try {
      Fiber.unsafeRunSync(run(args))
    } catch {
      // catch the awaiting semaphore in Fiber.unsafeRunSync
      case e: InterruptedException => println("Fiber.unsafeRunSync interrupted")
    } finally sleepers.shutdown()
}
