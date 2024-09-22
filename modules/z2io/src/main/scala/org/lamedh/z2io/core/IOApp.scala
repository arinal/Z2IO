package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

trait IOApp {

  def run(args: Array[String]): IO[Unit]

  final def main(args: Array[String]) = IO.unsafeRunSync(run(args))
}

object IOApp {

  trait Simple {
    def run: IO[Unit]
    final def main(args: Array[String]) = IO.unsafeRunSync(run)
  }

  implicit val sleepers: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
  implicit val ec: ExecutionContext               = scala.concurrent.ExecutionContext.Implicits.global
}
