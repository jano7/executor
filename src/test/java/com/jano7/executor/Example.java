package com.jano7.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Example {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(20);
        KeySequentialRunner<String> runner = new KeySequentialRunner<>(underlyingExecutor);

        String tradeIdA = "327";
        String tradeIdB = "831";
        Runnable task = new Runnable() {
            @Override
            public void run() {
                // process a message for the trade
            }
        };

        runner.run(tradeIdA, task); // execute the task by the underlying executor

        runner.run(tradeIdB, task); // execution is not blocked by the task for tradeIdA

        runner.run(tradeIdA, task); // execution starts when the previous task for tradeIdA completes

        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);

        Runnable keyRunnable =
                new KeyRunnable<>(tradeIdA, task); // helper class delegating hashCode and equals to the key

        executor.execute(keyRunnable);

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(30, TimeUnit.SECONDS);
    }
}
