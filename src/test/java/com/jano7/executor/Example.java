package com.jano7.executor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Example {

    public static void main(String[] args) throws InterruptedException {
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
    }
}
