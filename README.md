# Java Key Sequential Executor
This small library provides a optimized solution to a problem where tasks for a particular key need to be processed
sequentially as they arrive. This kind of problem can be solved by a [SingleThreadExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor--),
however it is not efficient. The issue is that the tasks for unrelated keys are not being processed in parallel, instead
they are put into a queue common to all keys and wait for the single thread to execute them. This library allows them to
be executed concurrently.   

## Example
Typical scenario in order management or booking systems is that messages for a particular trade **A** must be processed
sequentially in the same order as they are received (otherwise the state of the trade will be incorrect). The same is
true for any other trade - for example messages for the trade **B** must be processed sequentially as well. However it
is desirable that a message for the trade **A** does not block processing of a message for the trade **B** (and vice
versa) i.f they happen to arrive at the same time.
```
ExecutorService underlyingExecutor = Executors.newFixedThreadPool(20);
KeySequentialRunner<String> runner = new KeySequentialRunner<>(underlyingExecutor);

String tradeId = "1214324";
Runnable task = new Runnable() {
    @Override
    public void run() {
        // process the message e.g. cancel the trade
    }
};

runner.run(tradeId, task);
```

```
KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
Runnable keyRunnable = new KeyRunnable<>(tradeId, task);

executor.execute(keyRunnable);
```
## Maven Dependency
```
<dependency>
  <groupId>com.jano7</groupId>
  <artifactId>executor</artifactId>
  <version>1.0.0</version>
</dependency>
```
