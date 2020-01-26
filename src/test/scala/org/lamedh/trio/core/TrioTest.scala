package org.lamedh.trio.core

import org.scalatest.Matchers
import org.scalatest.TestSuite
import org.scalatest.FlatSpec
import org.lamedh.trio.core.Trio.IO
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import org.scalatest.FunSuite

class TrioTest extends FunSuite with Matchers {

  test("for comprehension") {
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

  test("error handling") {
    var count = 0
    var never = 0
    val io = for {
      _ <- IO(count += 1).handleError(_ => never += 1)
      _ <- IO(count += 1).handleError(_ => never += 1)
      _ <- IO(throw new Exception("Boom")).handleError(_ => count += 1)
      _ <- IO.raise(new Exception("Boom"))
    } yield ()

    io.handleErrorWith(_ => IO(count += 1)).unsafeRunSync()
    count shouldBe 4
    never shouldBe 0
  }

  // it should "blah" in {
  //   val io = for {
  //     a <- IO.async { (cb: Either[Throwable, Int] => Unit) =>
  //           Future(5).onComplete {
  //             case Success(v) => cb(Right(5))
  //             case Failure(t) => cb(Left(t))
  //           }
  //         }
  //     b <- IO.pure(2)
  //   } yield {
  //     (a + b)
  //   }

  //   io.unsafeRunAsync()
  // }
}
