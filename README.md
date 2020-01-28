# Z2IO

> For whom who has nosey curiosity about the internal machinery

An implementation of I/O monad to mimic the likes of ZIO and cats-effect. The aims of this project is educational, since the implementation is meant to be simple and easy to understand whilst also has the key features offered by a complete I/O monad frameworks.

The main concepts to be discussed in this document are:
- Scheduler
  - Preemptive vs cooperative multithreading
  - M:N scheduler
  - Fibers vs kernel (JVM) threads
  - Asynchronous boundary
  - Runloop as an elegant way to implement M:N scheduler (without building shceduler)
- Functional programming
  - Free monad (IO are only ADTs with runloop as interpreter)
  - Trampoline

> What is in a name?

Z2IO is not ZIO2. The previous sentence is also not a funny recursive acronym like "GNU not UNIX". Z2IO is Zero to IO, as in implementing it from zero until finished with a complete I/O framework.

## Usage
See how it is being used in [unit test](https://github.com/arinal/Z2IO/blob/master/src/test/scala/org/lamedh/z2io/core/Z2ioTest.scala).


### Acknowledgments
Special thanks to Fabio Labella [GitHub / Gitter](https://github.com/systemfw) who conducted a good presentation about Cats Effect internal.
