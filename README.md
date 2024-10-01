# Z2IO

> To the inquisitive minds seeking to understand the internal mechanisms. See the [slides](https://hackmd.io/@9Tvcj_JcSGGwklB8pDvExA/ry4yKS1A0)

An implementation of IO monad to mimic the likes of [ZIO](https://zio.dev/) and [cats-effect](https://typelevel.org/cats-effect/).
The aim of this project is educational, the implementation is meant to be simple
and easy to understand whilst also having the key features offered by complete IO monad frameworks.

> Z2IO is smaller than Cats Effect (300 vs. 30K+ lines of code), making it an ideal starting point to learn the internal.

The main concepts to be discussed in this document are:
- Scheduler
  - Preemptive vs cooperative multithreading
  - M:N scheduler
  - Fibers vs kernel (JVM) threads
  - Asynchronous boundary
  - Semantic blocking
- Functional programming
  - Free monad (IO instances are only ADTs and interpreted by the runloop)
  - Trampoline
  - Continuation passing style for async operation

> What's in a name?

Z2IO is not ZIO2. It's also not a playful recursive acronym like GNU's "GNU is Not UNIX".
Z2IO stands for "Zero to IO," signifying the journey of development from scratch to a fully-fledged IO framework.

## Main functionality
How it is being used in [Main.scala](https://github.com/arinal/Z2IO/blob/master/modules/examples/src/main/scala/Main.scala)
and [unit test](https://github.com/arinal/Z2IO/blob/master/modules/z2io/src/test/scala/org/lamedh/z2io/core/IOTest.scala)
Here's a quick overview of the key features.
```scala
import org.lamedh.z2io.core.Z2IO.IO
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

val io = for {
  _ <- IO.pure(5)   // Wraps an existing value into IO.
  _ <- IO(launch()) // Side effects can be wrapped with delay. The wrapped expression will be when the IO is run.
  _ <- IO.async[Unit] { cb => // IO.async can be used to wrap an async operation.
        launchAsync().onComplete {
          case Success(v) => cb(Right(v))
          case Failure(t) => cb(Left(t))
        }
      }
  _ <- IO.fromFuture(launchAsync())    // Does the exact same thing as previous operation

  five <- IO(throw new Exception("Boom")) // Exception is caught during the IO evaluation.
         .handleError(_ => 5)             // Then it's handled with a constant 5.

  // both of the following operations will be executed in parallel
  f1 <- IO.fork(calculatePi)
  f2 <- IO.fork(calculateE)
  // join operation will wait for the completion of the forked operation
  pi <- f1.join
  e  <- f2.join

  _ <- IO.never     // this makes our io never reach completion
} yield ()
```

Up until this point, no magic has happened yet, since `for` comprehension is only a syntactic sugar for calling `flatMap` and `map`.
The [code](https://github.com/arinal/Z2IO/blob/master/src/main/scala/org/lamedh/z2io/core/Z2IO.scala#L16-L17)
only constructs `Map` and `Flatmap` case classes. In fact, every operator inside `IO` (except something that has `unsafe` and `run`)
is only composing the IO with "dummy" case classes such as `Pure`, `Delay`, `Async`, `Map`. That is, without an interpreter which can interpret our dummy data structures, our composed `io` is useless.
Executing `io.unsafeRunSync()` amongst others, will put the composed `io` into the interpreter and start the execution.

Let's see what the construction looks like by printing it.
```scala
println(io)
```

The above statement prints:
```scala
Flatmap(Pure(5),org.lamedh.Main$$$Lambda$)
```
Note that the printed structure is incomplete because it should also contain `Map`, `Delay`, `Async`, and `HandleError`.
The fact that the printed structure is incomplete is interesting because the lambda parameter inside the nested `flatMap` hasn't been evaluated yet.
In a different context, the incomplete structure is also what makes [trampolining](https://github.com/arinal/Z2IO/blob/b57c47b9c202188d5036c85d769a21aee45ac299/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala#L24-L36) possible.

Run the `io` by wrapping it with a fiber, then run its run method:
```scala
import org.lamedh.z2io.core.Fiber
val fiber = Fiber(io)
fiber.unsafeRunSync()
```
Fiber owns the runloop which will interpret all of the structures constructed in the previous `for` comprehension.
If async boundary is hit, it's blocked until the async handler is finished.
Since `IO.never` is also incorporated, this will block the main thread forever.

Other important concepts are semantic blocking and yielding. The `IO.sleep` below won't block the current thread since
it internally uses `ScheduledExecutorService` to schedules the continuation.

```scala
object Main extends IOApp {

  def log(msg: String) = IO(println(s"${Thread.currentThread().getName}: $msg"))
 
  def run(args: Array[String]): IO[Unit] =
    for {
      _ <- log("Hello") *> IO.shift *> log("world")
      _ <- log("Wait 1 second") *> IO.sleep(1.second) *> log("Thanks for waiting!")
    } yield ()
}
```

### Acknowledgments
Special thanks to Fabio Labella [GitHub / Gitter](https://github.com/systemfw) who delivered a good presentation about Cats Effect internal.

Built with [NeoVim](https://neovim.io/) and [metals](https://scalameta.org/metals/). Proudly crafted without an IDE :)
