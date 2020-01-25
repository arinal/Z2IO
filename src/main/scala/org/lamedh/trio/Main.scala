package org.lamedh.trio

import org.lamedh.trio.core.Trio._

object Main extends App {

  val io = for {
    _ <- IO(println("This"))
    _ <- IO(println("is"))
    _ <- IO(println("Genesis"))
  } yield ()

  io.unsafeRunSync()
}
