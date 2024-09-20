package org.lamedh.z2io.core

import org.lamedh.z2io.core.Z2IO.IO
import java.util.concurrent.Executors

trait IOApp {

  def run(args: Array[String]): IO[Unit]

  final def main(args: Array[String]) = IO.unsafeRunSync(run(args))
}
