/*
MIT License

Copyright (c) 2020 Jan Gaspar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.jano7.executor;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class BoundedExecutorTest {

    @Test(timeout = 5000)
    public void blocksWhenLimitReached() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Runnable blockingTask = new KeyRunnable<>("key", () -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            completed.incrementAndGet();
        });
        Runnable simpleTask = new KeyRunnable<>("key", completed::incrementAndGet);

        BoundedExecutor bounded = new BoundedExecutor(5, executor);
        bounded.execute(blockingTask);
        bounded.execute(simpleTask);
        bounded.execute(simpleTask);
        bounded.execute(simpleTask);
        bounded.execute(simpleTask);

        AtomicBoolean submitted = new AtomicBoolean(false);
        Thread t = new Thread(() -> {
            bounded.execute(simpleTask);
            submitted.set(true);
            done.countDown();
        });
        t.start();
        t.join(1000L);

        assertFalse(submitted.get());

        block.countDown();
        done.await();

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        assertEquals(6, completed.get());
        assertTrue(submitted.get());
    }

    @Test(expected = NullPointerException.class)
    public void throwExceptionWhenTaskIsNull() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        KeySequentialExecutor executor = new KeySequentialExecutor(underlying);
        Executor bounded = new BoundedExecutor(10, executor);

        try {
            bounded.execute(null);
        } finally {
            underlying.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void releaseLockOnException() {
        Executor underlying = command -> {
            throw new RejectedExecutionException();
        };
        KeySequentialExecutor executor = new KeySequentialExecutor(underlying);
        Executor bounded = new BoundedExecutor(1, executor);

        int caught = 0;
        try {
            bounded.execute(() -> {
            });
        } catch (RejectedExecutionException e) {
            caught += 1;
        }
        try {
            bounded.execute(() -> {
            });
        } catch (RejectedExecutionException e) {
            caught += 1;
        }
        assertEquals(2, caught);
    }
}
