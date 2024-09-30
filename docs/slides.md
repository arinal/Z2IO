### Why IO Monad
```scala
def printPerson(person: Person) = println(s"Hi! My name is ${person.name}")

def validateName(name: String): Either[String, String] = ???
def validateAge(age: Int): Either[String, Int] = ???
def validate(person: Person): Either[String, Person] =
  (validateName(person.name), validateAge(person.age)).mapN((n, a) => Person(n, a))

val person = Person(...)
validatePerson(person).map(printPerson)
```

---

### Side effect is not composable

```scala
// funny way of composition
def printPerson(person: Person) = println(s"Hi! My name is ${person.name}")

def validateName(name: String): Unit = if (name.empty) println("Invalid name") else ("name is valid")
def validateAge(age: Int): Unit  = if (age < 0) println("Invalid age") else ("age is valid")
def validate(person: Person): Unit = {
  validateName(person.name)
  validateAge(person.age)
}

val person = Person(...)
validatePerson(person)
// we don't know if it's valid or not!

printPerson(person) // Person is printed even though it's not valid
```

> Side effect must be done at the end of the world!

---

### Wrapping side effect
```scala
class IO[A](value: => A) {
  def map    (f: A => B)    : IO[B] = new IO(f(value))
  def flatMap(f: A => IO[B]): IO[B] = f(value)

  def run(): A = value()
}

object IO {
  apply[A](value: => A) = IO(() => A)
}

val io = IO(printPerson(person))
io.run()
```

---

### One value to run them all
```scala
val io1: IO[Either[String, Person]] = IO(validatePerson)
val io2: IO[Either[String, Unit]]   = io1.map(ep => println(ep)))
io2.run()
```

---

### Data Type
```scala
sealed trait IO[+A] {
    def map[B](f: A => B): IO[B]         = Map(this, f)
    def flatMap[B](f: A => IO[B]): IO[B] = Flatmap(this, f)
}

final case class Pure[A](a: A)                           extends IO[A]
final case class Delay[A](a: () => A)                    extends IO[A]
final case class Map[A, B](io: IO[A], f: A => B)         extends IO[B]
final case class Flatmap[A, B](io: IO[A], f: A => IO[B]) extends IO[B]
																			 
final case class ErrorFlatmap[A, B](io: IO[A], h: Throwable => IO[B]) extends IO[B]
final case class RaiseError[T <: Throwable](t: T)                     extends IO[Nothing]
final case class Async[A](k: (Either[Throwable, A] => Unit) => Unit)  extends IO[A]
```

---

### Value Wrapped on an IO

```scala
val pure5     = Pure(5)                // Pure(a = 5)
val pureUnit  = Pure(println(" :) "))  // Delay(a = ())
val delay     = Delay(5)               // Delay(a = () => 5)
val delayUnit = Delay(println(" :) ")) // Delay(a = () => println(" :) ")

val fmap = Flatmap(pure5, n => Pure(n + 1)) // Flatmap(pure5, anon_lambda)
val fmap2 = pure5.flatMap(n => Pure(n + 1)) // Flatmap(pure5, anon_lambda)
```

---

### A monad to bind multiple IO together

Our program is not consisted from list of IO, but from a single root IO composed by smaller IO bound together.

> One IO to rule them all, One IO to find them, One IO to bring them all and in the darkness **BIND** them.

---

Bind operation is basically a `flatMap`:
```scala
def flatMap[A, B](io: IO[A], f: A => IO[B]): IO[B]

val square2: IO[Int] = IO(2 * 2)
def printInt(n: Int): IO[Unit] = IO(println(n))
val finished: IO[Unit] = IO(println("Program finished"))

val squareAndPrint: IO[Unit] = Flatmap(square2, n => printInt)
val rootIO: IO[Unit] = Flatmap(squareAndPrint, _ => finished)

run(rootIO)
```

---

### Composing IO

```scala
val root: IO[Unit] =
  for {
    _    <- IO(print("name: "))
    name <- IO(readLine())
    _    <- IO(println(name))
  } yield ()

// desugars into
Delay(() => print("name: "))
  .flatMap(_ =>
    Delay(() => readLine())
	  .map(name => println(name))
  )

// which equivalent with
Flatmap(
  io = Delay(() => print("name: ")),
  f  = _ => {
    Flatmap(
	  io = Delay(() => readLine())
	  f  = name => Delay(() => println(name))
    )
  }
)

// another arrangement which does exactly the same
Flatmap(
  io = Flatmap(
    io = Delay(() => print("name: ")),
    f = _ => Delay(() => readLine())
  ),
  f = name => Delay(() => println(name))
)
```

---

### IO execution is lazy

IO construct is created iteratively. Imagine a for comprehension with hundreds of `flatMap`:
```scala
val program =
  for {
    _ <- Delay(() => print("start"))
    _ <- io1
    _ <- io2
    // 100 io later..
    // ...
    _ <- io100
    _ <- Delay(() => print("finish")
  } yield ()
```

The instantiated object are only 2 lambdas, a `Delay`, and a `Flatmap`:
```scala
Flatmap(          // a Flatmap
  Delay(lambda1), // a Delay and its lambda
  lambda2         // a lambda that if its apply executed, will discover another IO
)
```

---

### Function is static

```scala
class AClass(val field: Int) {
  def foo(): Int = field + 5
}

def bar = {
  val aClass  = new AClass(5)
  aClass.foo()

  val lambda0 = (p: Int) = p + p
  lambda0(5)
  
  val maybe = for {
    a <- Some(1)
    b <- Some(2)
    c <- Some(3)
  } yield a + b + c

  // desugared into:
  // Some(1).flatMap(a =>
  //   Some(2).flatMap(b =>
  //     Some(3).map(c => a + b + c)
  //   )
  // )
}
```

---

### Function is static

The preallocated static functions are:
```scala
def AClass_foo(me: AClass): Int =
  me.field + 5

def Option_flatmap(me: Option, f: A => Option[B]): Option[B]
def Option_map(me: Option, f: A => B): Option[B]

def Lambda0_apply(me: Lambda0, p: Int): Int =
  p + p

def bar = {
    val aClass = new AClass(5)
    AClass_foo(aClass)         // returns 10
    val lambda0 = new Lambda0
    Lambda0_apply(lambda0, 5)  // returns 10
    val lambda1 = new Lambda1
    flatmap_option(Some(1), lambda1) // returns Some(5)
}

def Lambda1_apply(me: Lambda1, a: Int): Option[A] = {
    val lambda2 = new Lambda2(a)
    option_flatmap(Some(2), lambda2)
}

def Lambda2_apply(me: Lambda2, b: Int) = {
    val lambda3 = new Lambda3(me.a, b)
    option_map(Some(3), lambda3)
}

def Lambda3_apply(me: Lambda3, c: Int) =
    me.a + me.b + c
```

### Run Loop

```scala
def unsafeRunSync(io: IO[A]): A = {
  def loop(current: IO[Any], stack: Stack[Any => IO[Any]]): A = {
    current match {
	  case FlatMap(io, f) => loop(io          , stack.push(f))
	  case Delay(body)    => loop(Pure(body()), stack)
	  case Pure(v) =>
	    stack.pop match {
		  case Some((f, stack)) => loop(f(v), stack)
		  case None             => v.asInstanceOf[A] // end of the loop
		}
	}
  }
  loop(io, Stack.empty)
}
```

---

### Error Handling


---

### Ayns construct

```scala
type Callback[A] = Either[Throwable, A] => Unit
case class Async[A](f: Callback[A] => Unit) extends IO[A]
```

How it's used from user side:
```scala
val async: IO[Employee] = IO.Async { cb =>
   val fut = getEmployeeById("E01")
   fut.onFinished {
     Some(employee=> cb(employee))
   }
}
```

How it's evaluated inside runloop:
```scala
def loop[A](current: IO[Any], stack: List[Bind], finishCb: Callback[A]): Unit =
  current match {
  ...
    case Async(f) => {
      // Before calling f, make sure that cb will continue the runloop
      // when it's called (inside f).
      val cb: Callback[A] = res => loop(res.fold(raise, pure), stack, finishCb)
      f(cb) // f is called here, which will invoke getEmployeeById
```

---

### Async operation inside sync execution

![async-in-sync-exec](https://hackmd.io/_uploads/ry0lnS1AR.png)

---
### M:N Scheduler

![Pasted image 20240923092251](https://hackmd.io/_uploads/rkNAtMg00.png)

---

### Challenges

1. Draw the state of the run loop (stack condition, current IO) for:
   - `Delay(() => println("Hi")`
   - ```scala
     for {
       _ <- IO(println("Hello")
       _ <- IO(println("World")
     yield ()
     ```
	 
2. There is a function `sum` defined below:
   ```scala
     def sum(n: Long): IO[Long] =
       if (n == 0) IO.pure(0)
       else IO.pure(n).flatMap(p => sum(n - 1).map(_ + n))
   ```
   Analyze when we call `sum(3)`, draw the state of the run loop. Can it handle `sum(500000)`? How it's not triggerring a stack overflow?

3. Create a construct to repeat an IO N time by repeatedly filling the stack with the bind. Why does this solution isn't ideal?

4. If we change the implicit execution context on `IOApp` into:
   ```scala
   implicit val sleepers: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
   implicit val ec: ExecutionContext               = ExecutionContext.global
   ```
     Can you spot the difference during the execution of `general`? Without changing the implicit execution context, how to fix this?

5. Implement `trait Fiber` so that all of the forked computation can be joined!
   ```scala
   def ticking(res: Int): IO[Int] =
     (IO(log("Tick")) *> IO.sleep(1.second)).repeat(3) *> IO.pure(res)

   for {
     f1 <- IO.fork(ticking(1))
     f2 <- IO.fork(ticking(2))
     f3 <- IO.fork(ticking(3))
     
     n1 <- f1.join // wait for f1 to finish, then get the result
     n2 <- f2.join
     n3 <- f3.join
   
     _ <- log(s"result: ${n1 + n2 + n3}")
   } yield ()
   ```
