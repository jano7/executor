package com.jano7.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Example {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(20);
        KeySequentialRunner<String> runner = new KeySequentialRunner<>(underlyingExecutor);

        String tradeId = "1214324";
        Runnable task = new Runnable() {
            @Override
            public void run() {
                System.out.println("e.g. book, amend or cancel the trade");
            }
        };

        runner.run(tradeId, task);

        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
        Runnable keyRunnable = new KeyRunnable<>(tradeId, task);

        executor.execute(keyRunnable);

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(30, TimeUnit.SECONDS);
    }
}
