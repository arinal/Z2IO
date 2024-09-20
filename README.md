# Z2IO

> To those who are curious about internal machinery

An implementation of IO monad to mimic the likes of [ZIO](https://zio.dev/) and [cats-effect](https://typelevel.org/cats-effect/).
The aim of this project is educational, the implementation is meant to be simple and easy to understand whilst also having the key features offered by complete IO monad frameworks.

The main concepts to be discussed in this document are:
- Scheduler
  - Preemptive vs cooperative multithreading
  - M:N scheduler
  - Fibers vs kernel (JVM) threads
  - Asynchronous boundary
  - Building M:N scheduler without actually making a scheduler by using runloop
  - Semantic blocking
- Functional programming
  - Free monad (IO instances are only ADTs with runloop as interpreter)
  - Trampoline
  - Continuation passing style for async operation

> What's in a name?

Z2IO is not ZIO2. The previous sentence is also not a funny recursive acronym like GNU's "*GNU is not UNIX*". Z2IO is "*Zero to IO*",
as in development from zero until reaching a complete (and hopefully matured) IO framework.

## Main functionality
How it is being used in [unit test](https://github.com/arinal/Z2IO/blob/master/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala).

Below are the main functionalities at a glance.

```scala
import scala.concurrent.Future
import org.lamedh.z2io.core.Z2IO.IO
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

val io = for {
  _ <- IO.pure(5)         // use pure only if we have the value already, don't ever use it to wrap expression with side effects
  _ <- IO.delay(launch()) // side effects can be wrapped with delay. The wrapped expression will be evaluated inside the runloop
  _ <- IO(launch())       // IO.apply is an alias for IO.delay. As with previous operation, will be evaluated on the same thread
  _ <- IO.async[Unit] { cb => // IO.async can be used to wrap async operation. Here, launchAsync() returns Future
        launchAsync().onComplete {
          case Success(v) => cb(Right(v))
          case Failure(t) => cb(Left(t))
        }
      }
  _ <- IO.fromFuture(launchAsync())    // helper function for handling async future, does the exact same thing as previous operation
  _ <- IO(throw new Exception("Boom")) // throwing error inside delay construct
         .handleError(_ => 5)          // thrown error is handled and IO of value 5 is returned instead
  _ <- IO.never                        // this makes our io never reach completion
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
Flatmap(Pure(5),org.lamedh.Main$$$Lambda$7426/803391093@8628866)
```
Note that the printed structure is incomplete because it should also contain `Map`, `Delay`, `Async`, and `HandleError`.
The fact that the printed structure is incomplete is interesting because the lambda parameter inside the nested `flatMap` hasn't been evaluated yet.
In a different context, the incomplete structure is also what makes [trampolining](https://github.com/arinal/Z2IO/blob/b57c47b9c202188d5036c85d769a21aee45ac299/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala#L24-L36) possible.

Run the `io` by calling its `unsafeRunSync` method.

```scala
io.unsafeRunSync()
```
Now the runloop will interpret all of the structures constructed in the previous `for` comprehension.
If async boundary is hit, it waits (by using semaphore) until the async handler is finished.
Since `IO.never` is also incorporated, this will block the main thread forever.

Executing the async version won't block the main thread, even though the execution of `io` still won't reach an end due to `IO.never`.
```scala
io.unsafeRunAsync {
  case Right(value) => println("IO execution is finished (unlikely)")
  case Left(t)      => println("Error: " + t.getMessage)
}
```

Other important concepts are semantic blocking and yielding. The `IO.sleep` below won't block the current thread since it internally uses `ScheduledExecutorService` and schedules the continuation.
`IO.sleep` takes `ScheduledExecutorService` as an implicit parameter but rather than instantiating it yourself, using `IOApp` as an entry point is a clean way of providing all of the needed explicit values and also shutting down everything
after the `run` method has finished.

```scala
object Main extends IOApp {

  def log(msg: String) = IO(println(s"${Thread.currentThread().getName}: $msg"))
 
  def run(args: Array[String]): IO[Unit] =
    for {
      _ <- log("Hello") *> IO.shift *> log("world")
      _ <- log("Wait 1 second") *> IO.sleep(1.second) *> log("Thanks for waiting!") //
    } yield ()
}
```
Without invoking `unsafeRunSync` the program can still run because it is executed from `IOApp.main` method.

### Acknowledgments
Special thanks to Fabio Labella [GitHub / Gitter](https://github.com/systemfw) who delivered a good presentation about Cats Effect internal.

Build with love using [NeoVim](https://neovim.io/) and [metals](https://scalameta.org/metals/). Proudly made without IDE :)
