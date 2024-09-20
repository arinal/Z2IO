package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO
import java.util.concurrent.Executors

trait IOApp {

  def run(args: Array[String]): IO[Unit]

  final def main(args: Array[String]) = IO.unsafeRunSync(run(args))
}

object IOApp {
  implicit val sleepers = Executors.newScheduledThreadPool(1)
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global
}
