package com.jano7.executor;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.jano7.executor.BoundedStrategy.BLOCK;

public class BoundedExecutorTest {

    @Test(expected = NullPointerException.class)
    public void throwExceptionWhenTaskIsNull() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        Executor bounded = new BoundedExecutor(10, BLOCK, underlying);

        try {
            bounded.execute(null);
        } finally {
            underlying.shutdownNow();
        }
    }
}
