package org.lamedh.z2io.core

import org.lamedh.z2io.core.IO
import org.scalatest.AsyncFunSuite
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.TestSuite
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.concurrent.Await
import scala.concurrent.duration._

class IOTest extends AsyncFunSuite with Matchers {

  import IOTest._

  test("simple run sync") {
    run(IO.pure(2)) shouldBe 2
    run(IO(2)) shouldBe 2
    run(IO.Map[Int, Int](IO.pure(2), _ + 1)) shouldBe 3
  }

  test("simple run async") {
    runAsync(IO.pure(2)) shouldBe 2
    runAsync(IO(2)) shouldBe 2
    runAsync(IO.Map[Int, Int](IO.pure(2), _ + 1)) shouldBe 3
  }

  test("for comprehension") {
    run(for_2_IO) shouldBe 2
  }

  test("error handling") {
    run(handler_4_0_IO) shouldBe (4, 0)
  }

  test("stack safety with trampoline") {
    // Executing untrampolined version (with n = 500000) will blow the stack
    // def sum(n: Long) = if (n > 0) n + sum(n - 1) else 0
    // Also note that below is how not to do trampoline either:
    // {{{
    // def sum(n: Long): IO[Long] =
    //   if (n == 0) IO.pure(0) else sum(n - 1).map((l: Long) => l + n)
    // }}}
    // Tip: recursive call should be inside lambda
    def sum(n: Long): IO[Long] =
      if (n == 0) IO.pure(0)
      else IO.pure(n).flatMap(p => sum(n - 1).map(_ + n))

    run(sum(500000)) shouldBe 125000250000L
  }

  test("async: callback is called") {
    var _2 = 0
    IO.unsafeRunAsync[Int](IO(2), {
      case Right(v) => _2 = v
      case _        =>
    })
    _2 shouldBe 2
  }

  // test("async: handle error flow") {
  //   var _4_0 = (0, 0)
  //   IO.unsafeRunAsync(handler_4_0_IO, {
  //     case Right(tup) => _4_0 = tup
  //     case _          =>
  //   })
  //   _4_0 shouldBe (4, 0)
  // }

  // test("async: handle error") {
  //   var left = false
  //   val ioErr = IO { throw new Exception("Boom"); 5 }
  //   val cb: (Either[Throwable, Int] => Unit) = {
  //     case Left(_) => left = true
  //     case Right(n)       => ()
  //   }
  //   IO.unsafeRunAsync[Int](ioErr, cb)
  //   left shouldBe true
  // }

  test("from future and back to future") {
    val io = IO.fromFuture(Future.successful(5))
    IO.unsafeToFuture(io).map(_ shouldBe 5)
  }

  def run[A](io: IO[A]): A = IO.unsafeRunSync(io)
  def runAsync[A](io: IO[A]): A = Await.result(IO.unsafeToFuture(io), 1.second)

  def runAsync(name: String, io: IO[Int], expected: Int) = {
      var result = 0
      val sem = new java.util.concurrent.Semaphore(0)
      IO.unsafeRunAsync[Int](io, {
        case Right(v) =>
          result = v
          sem.release()
        case _        =>
      })
      sem.acquire()
      result
    }
}

object IOTest {

  val for_2_IO = for {
    _0 <- IO(0)
    _1 <- IO(_0 + 1)
    _2 <- IO(_1 + 1)
  } yield _2

  def handler_4_0_IO = {
    var (must4, never) = (0, 0)
    val io = for {
      _ <- IO(must4 += 1).handleErr(_ => never += 1) // must4: 1, never: 0
      _ <- IO(must4 += 1).handleErr(_ => never += 1) // must4: 2, never: 0
      _ <- IO(throw new Exception("Boom")).handleErr(_ => must4 += 1) // must4: 3, never: 0
      _ <- IO.raise(new Exception("Boom"))
      _ <- IO(never += 1) // must4: 3, never: 0
    } yield (must4, never)
    io.handleErr(_ => must4 += 1 /* must4: 4, never: 0 */ ) *> IO(must4 -> never)
  }
}
