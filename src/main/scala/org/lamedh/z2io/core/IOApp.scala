package org.lamedh.z2io.core

import org.lamedh.z2io.core.Z2IO.IO
import java.util.concurrent.Executors

trait IOApp {

  implicit val sched = Executors.newScheduledThreadPool(1)

  def run(args: Array[String]): IO[Unit]

  final def main(args: Array[String]) =
    try run(args).unsafeRunSync() finally sched.shutdown()
}
