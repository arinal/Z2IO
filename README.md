# Z2IO

> To whom who has nosey curiosity about the internal machinery

An implementation of IO monad to mimic the likes of [ZIO](https://zio.dev/) and [cats-effect](https://typelevel.org/cats-effect/).
The aims of this project is educational, the implementation is meant to be simple and easy to understand whilst also has the key features offered by a complete IO monad frameworks.

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

Z2IO is not ZIO2. The previous sentence is also not a funny recursive acronym like GNU's "*GNU is not UNIX*". Z2IO is "*Zero to IO*",
as in development from zero until reaching a complete (and hopefully matured) IO framework.

## Main functionality
See how it is being used in [unit test](https://github.com/arinal/Z2IO/blob/master/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala).

Below is the main functionalities at a glance.

```scala
import scala.concurrent.Future
import org.lamedh.z2io.core.Z2IO.IO
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

val io = for {
  _ <- IO.pure(5)         // use pure only if we have the value already, don't ever use it to wrap expression with side effects
  _ <- IO.delay(launch()) // side effects can be wrapped with delay. The wrapped expression will be evaluated when IO.run is called
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

Up until this point, there is no magic happened yet. For comprehension is only a syntactic sugar for calling `flatMap` and `map`
and by peeking the [code](https://github.com/arinal/Z2IO/blob/ec5417350b9ae493f8162e43e2edb1e717a2f87d/src/main/scala/org/lamedh/z2io/core/Z2IO.scala#L18-L19),
it only constructs `Map` and `Bind` case classes. In fact, every operator inside `IO` (except something that has `unsafe` and `run`)
is only composing the IO with "dummy" case classes such as `Pure`, `Delay`, `Async`, `Map`. That is, without some interpreter which can interpret our dummy data structures, our composed `io` is useless.
Executing `io.unsafeRunSync()` amongst other, will put the composed `io` into the interpreter and start the execution.

Let's see what the construction looks like by printing it.
```scala
println(io)
```

The above statement prints:
```scala
Bind(Pure(5),org.lamedh.Main$$$Lambda$7426/803391093@8628866)
```
Note that the printed structure is incomplete because it should also contains `Map`, `Delay`, `Async`, and `HandleError`.
The fact that the printed structure is incomplete is interesting because the lambda parameter inside the nested `flatMap` hasn't been evaluated yet.
In different context, incomplete structure is also what makes [trampolining](https://github.com/arinal/Z2IO/blob/b57c47b9c202188d5036c85d769a21aee45ac299/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala#L24-L36) possible.

Run the `io` by calling its `unsafeRunSync` method.

```scala
io.unsafeRunSync()
```
Now the runloop will interpret all of the structures constructed in the previous `for` comprehension.
If async boundary is hit, it waits (by using semaphore) until the async handler is finished.
Since `IO.never` is also incorporated, this will block the main thread forever.

Executing the async version won't block the main thread, even though the execution of `io` still won't be finished due to `IO.never`.
```scala
io.unsafeRunAsync {
  case Right(value) => println("IO execution is finished (unlikely)")
  case Left(t)      => println("Error: " + t.getMessage)
}
```

> TODO: add extra documentations

### Acknowledgments
Special thanks to Fabio Labella [GitHub / Gitter](https://github.com/systemfw) who conducted a good presentation about Cats Effect internal.

Build with love using [NeoVim](https://neovim.io/) and [metals](https://scalameta.org/metals/). Proudly made without IDE :)
