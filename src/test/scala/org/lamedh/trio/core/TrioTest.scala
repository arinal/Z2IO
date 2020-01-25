package org.lamedh.trio.core

import org.scalatest.Matchers
import org.scalatest.TestSuite
import org.scalatest.FlatSpec
import org.lamedh.trio.core.Trio.IO

class TrioTest extends FlatSpec with Matchers {

  "Trio" should "compatible with for comprehension" in {
    var count = 0
    val io = for {
      _ <- IO(count += 1)
      _ <- IO(count += 1)
      _ <- IO(count += 1)
    } yield ()

    count shouldBe 0
    io.unsafeRunSync()
    count shouldBe 3
  }

  "Trio" should "handle error properly" in {
    var count = 0
    val io = for {
      _ <- IO(count += 1).handleError(_ => println("never"))
      _ <- IO(count += 1).handleError(_ => println("never"))
      _ <- IO(new Exception("Boom")).handleError(_ => count += 1)
      _ <- IO.raise(new Exception("Boom"))
    } yield ()

    io.handleErrorWith(_ => IO(count += 1)).unsafeRunSync()
    count shouldBe 4
  }
}
