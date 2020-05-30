# Key Sequential Executor
This small library provides an optimized solution to a problem where tasks for a particular key need to be processed
sequentially as they arrive. This kind of problem can be solved by a [`SingleThreadExecutor`](
https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor--), however it is
not efficient. The issue is that the tasks for unrelated keys are not being processed in parallel, instead they are put
into a queue common to all keys and wait for the single thread to execute them. This library allows them to be executed
concurrently. Moreover this library works well in a situation where all the possible keys and their number is **not**
known upfront.
## Usage
A typical scenario in order management or booking systems is that messages for a particular trade **A** must be
processed sequentially in the same order as they are received (otherwise the state of the trade will be incorrect). The
same is true for any other trade - for example messages for the trade **B** must be processed sequentially as well.
However it is desirable that a message for the trade **A** does not block processing of a message for the trade **B**
(and vice versa) if they happen to arrive at the same time.
```java
ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
KeySequentialRunner<String> runner = new KeySequentialRunner<>(underlyingExecutor);

String tradeIdA = "327";
String tradeIdB = "831";
// more Trade IDs can arrive in a real scenario, but it is usually not known how many upfront

Runnable task = new Runnable() {
    @Override
    public void run() {
        // process a message for the trade
    }
};

runner.run(tradeIdA, task); // execute the task by the underlying executor

runner.run(tradeIdB, task); // execution is not blocked by the task for tradeIdA

runner.run(tradeIdA, task); // execution starts when the previous task for tradeIdA completes
```
In the example above the key is a Trade ID. Tasks for a particular Trade ID are executed sequentially but they do not
block tasks for other Trade IDs (unless the tasks are blocked by the underlying executor).

Please note the Key needs to correctly implement `hashCode` and `equals` methods as the implementation stores the tasks
in a `HashMap`.

If you require an [`Executor`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) you can use
[`KeySequentialExecutor`](src/main/java/com/jano7/executor/KeySequentialExecutor.java) instead of
[`KeySequentialRunner`](src/main/java/com/jano7/executor/KeySequentialRunner.java) which accepts
[`Runnable`](src/main/java/com/jano7/executor/KeyRunnable.java) delegating its `hashCode` and `equals` methods to the
key.
```java
Executor executor = new KeySequentialExecutor(underlyingExecutor);

Runnable runnable =
        new KeyRunnable<>(tradeIdA, task); // helper class delegating hashCode and equals to the key

executor.execute(runnable);
```

The `KeySequentialExecutor` and `KeySequentialRunner` do not support back-pressure. It means that `execute` and `run`
methods never block, instead the submitted tasks are put into a queue where they wait until executed by the underlying
executor. In many cases this is not a problem, however in some situations it may cause an application to run out of
memory as the number of waiting tasks grows. If you want to restrict the number of queued tasks, consider use of a
[`KeySequentialBoundedExecutor`](src/main/java/com/jano7/executor/KeySequentialBoundedExecutor.java) which can be
configured to block the task submission when the number of tasks, which haven't been executed yet, reaches the limit.
```java
ExecutorService underlyingExecutor = Executors.newCachedThreadPool();
int maxTasks = 10;
KeySequentialBoundedExecutor boundedExecutor =
        new KeySequentialBoundedExecutor(maxTasks, BoundedStrategy.BLOCK, underlyingExecutor);

KeyRunnable<String> task = new KeyRunnable<>("my key", () -> {
    // do something
});

boundedExecutor.execute(task);

// execute more tasks ... at most 10 will be scheduled

// before shutting down you can call a 'drain' method which blocks until all submitted task have been executed
boundedExecutor.drain(timeout, TimeUnit.SECONDS); // returns true if drained; false if the timeout elapses

// newly submitted tasks will be rejected after calling 'drain'

underlyingExecutor.shutdownNow(); // safe to call 'shutdownNow' if drained as there should be no active tasks
```
The source code of the examples can be found [here](src/test/java/com/jano7/executor/Examples.java).

A note on **thread-safety**: The library is thread-safe; i.e. methods `run`, `execute` or `drain` can be safely invoked
from multiple threads without synchronization.
## Maven Dependency
```xml
<dependency>
  <groupId>com.jano7</groupId>
  <artifactId>executor</artifactId>
  <version>2.0.0</version>
</dependency>
```
