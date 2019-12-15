# Java Key Sequential Executor
This small library provides a optimized solution to a problem where tasks for a particular key need to be processed
sequentially as they arrive. This kind of problem can be solved by a [SingleThreadExecutor](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executors.html#newSingleThreadExecutor--),
however it is not efficient. The issue is that the tasks for unrelated keys are not being processed in parallel, instead
they are put into a queue and wait for the single thread to execute them. This library allows them to be executed
concurrently.   

## Example
```
ExecutorService underlyingExecutor = Executors.newFixedThreadPool(20);
KeySequentialExecutor runner = new KeySequentialExecutor(underlyingExecutor);

String tradeId = "1214324";
Runnable task = new Runnable() {
    @Override
    public void run() {
        System.out.println("e.g. book, amend or cancel the trade");
    }
};
Runnable keyRunnable = new KeyRunnable<>(tradeId, task);

runner.execute(keyRunnable);

underlyingExecutor.shutdown();
underlyingExecutor.awaitTermination(30, TimeUnit.SECONDS);
```
