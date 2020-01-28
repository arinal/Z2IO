package org.lamedh.z2io.core

import org.scalatest.Matchers
import org.scalatest.TestSuite
import org.scalatest.FlatSpec
import org.lamedh.z2io.core.Z2IO.IO
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import org.scalatest.AsyncFunSuite

class Z2ioTest extends AsyncFunSuite with Matchers {

  import Z2ioTest._

  test("for comprehension") {
    for_2_IO.unsafeRunSync() shouldBe 2
  }

  test("error handling") {
    handler_4_0_IO.unsafeRunSync() shouldBe (4, 0)
  }

  test("stack safety with trampoline") {
    // Executing untrampolined version (with n = 500000) blows the stack
    // def sum(n: Long) = if (n > 0) n + sum(n - 1) else 0
    // Also note that below is how not to do trampoline either:
    // def sum(n: Long): IO[Long]
    //   if (n == 0) IO.pure(0) else sum(n - 1).map((l: Long) => l + n)
    // Tip: recursive call should be inside lambda
    def sum(n: Long): IO[Long] =
      if (n == 0) IO.pure(0)
      else IO.pure(n).flatMap(p => sum(n - 1).map(_ + n))

    sum(500000).unsafeRunSync() shouldBe 125000250000L
  }

  test("async: callback is called") {
    var _2 = 0
    for_2_IO.unsafeRunAsync {
      case Right(v) => _2 = v
      case _        =>
    }
    _2 shouldBe 2
  }

  test("async: handle error flow") {
    var _4_0 = (0, 0)
    handler_4_0_IO.unsafeRunAsync {
      case Right(tup) => _4_0 = tup
      case _          =>
    }
    _4_0 shouldBe (4, 0)
  }

  test("async: handle error") {
    var left = false
    IO { throw new Exception("Boom"); 5 }.unsafeRunAsync {
      case Left(_) => left = true
      case _       =>
    }
    left shouldBe true
  }

  test("from future and back to future") {
    IO.fromFuture(Future.successful(5))
      .unsafeToFuture
      .map(_ shouldBe 5)
  }
}

object Z2ioTest {

  val for_2_IO = for {
    a0 <- IO(0)
    a1 <- IO(a0 + 1)
    a2 <- IO(a1 + 1)
  } yield a2

  def handler_4_0_IO = {
    var (must4, never) = (0, 0)
    val io = for {
      _ <- IO(must4 += 1).handleError(_ => never += 1) // must4: 1, never: 0
      _ <- IO(must4 += 1).handleError(_ => never += 1) // must4: 2, never: 0
      _ <- IO(throw new Exception("Boom")).handleError(_ => must4 += 1) // must4: 3, never: 0
      _ <- IO.raise(new Exception("Boom"))
      _ <- IO(never += 1) // must4: 3, never: 0
    } yield (must4, never)
    io.handleError(_ => must4 += 1 /* must4: 4, never: 0 */ ) *> IO(must4 -> never)
  }
}
