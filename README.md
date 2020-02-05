# Z2IO

> To whom who has nosey curiosity about the internal machinery

An implementation of I/O monad to mimic the likes of ZIO and cats-effect. The aims of this project is educational, since the implementation is meant to be simple and easy to understand whilst also has the key features offered by a complete I/O monad frameworks.

The main concepts to be discussed in this document are:
- Scheduler
  - Preemptive vs cooperative multithreading
  - M:N scheduler
  - Fibers vs kernel (JVM) threads
  - Asynchronous boundary
  - Building M:N scheduler without actually making a scheduler by using runloop
- Functional programming
  - Free monad (IO instances are only ADTs with runloop as interpreter)
  - Trampoline
  - Continuation passing style for async operation

> What is in a name?

Z2IO is not ZIO2. The previous sentence is also not a funny recursive acronym like "GNU is not UNIX". Z2IO is "Zero to IO", as in implementing it from zero until it is finished with a complete matured I/O framework.

## Usage
See how it is being used in [unit test](https://github.com/arinal/Z2IO/blob/master/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala).

Below is all the main functionality at a glance.

```scala
import scala.concurrent.Future
import org.lamedh.z2io.core.Z2IO.IO
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

val io = for {
  _ <- IO.pure(5)   // 5 is evaluated eagerly, don't use it for wrapping side effects
  _ <- IO(throw new Exception("Boom")).handleError(_ => 5) // error is handled and IO with value 5 is returned
  _ <- IO(launch())           // launch() will be evaluated when IO.run is called. Executed on the same thread
  _ <- IO.async[Unit] { cb => // launchAsync() returns Future, IO.async can be used to wrap async operation
        launchAsync().onComplete {
          case Success(v) => cb(Right(v))
          case Failure(t) => cb(Left(t))
        }
      }
  _ <- IO.fromFuture(launchAsync()) // helper function for handling async future, does the same thing as previous operation
  _ <- IO.never                     // this will never ever completed
} yield ()
```

Up until this point, there is no magic happened yet since for comprehension is only a syntactic sugar for calling `flatMap` and `map`.
The only thing happened is constructing a nested `Bind` and several other constructs (`Pure`, `Delay`, `Async`).
We can just print it to see all the structure.

```scala
println(io)
```

The above statement prints:
```scala
Bind(Pure(5),org.lamedh.Main$$$Lambda$7426/803391093@8628866)
```
Note that the printed structure is incomplete because it should also contains `Delay`, `Async`, `HandleError` amongst other.
The fact that the printed structure is incomplete is interesting because the lambda parameter inside the nested `flatMap` hasn't been evaluated yet.
In different context, incomplete structure is also what makes [trampolining](https://github.com/arinal/Z2IO/blob/b57c47b9c202188d5036c85d769a21aee45ac299/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala#L24-L36) possible.

Run the `io` by calling its `unsafeRunSync` method.

```scala
io.unsafeRunSync()
```
Now the runloop will interpret all of the structures constructed in previous `for` comprehension.
If async boundary is hit, wait until the async handler is finished using semaphore.
Since we called `IO.never`, this will block the main thread forever.

We can also executes the async version even though the execution will still never be finished due to `IO.never`,  but now the main thread is not blocked.
```scala
io.unsafeRunAsync {
  case Right(value) => println("IO execution is finished (unlikely)")
  case Left(t)      => println("Error: " + t.getMessage)
}
```

> TODO: add extra documentations

### Acknowledgments
Special thanks to Fabio Labella [GitHub / Gitter](https://github.com/systemfw) who conducted a good presentation about Cats Effect internal.

Build with love using [NeoVim](https://neovim.io/) and [metals](https://scalameta.org/metals/).
