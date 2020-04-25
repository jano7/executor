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

        Thread t = new Thread(() -> {
            bounded.execute(simpleTask);
            done.countDown();
        });
        t.start();

        assertFalse(done.await(1, TimeUnit.SECONDS));

        block.countDown();
        done.await();

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        assertEquals(6, completed.get());
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
        Executor underlying = new Executor() {

            private int count = 0;

            @Override
            public void execute(Runnable command) {
                if (count++ == 0) {
                    throw new RejectedExecutionException();
                }
                command.run();
            }
        };

        KeySequentialExecutor executor = new KeySequentialExecutor(underlying);
        Executor bounded = new BoundedExecutor(1, executor);

        boolean thrown = false;
        try {
            bounded.execute(() -> {
            });
        } catch (RejectedExecutionException e) {
            thrown = true;
        }
        bounded.execute(() -> {
        });

        assertTrue(thrown);
    }

    @Test(timeout = 5000)
    public void drain() throws InterruptedException {
        ExecutorService underlying = Executors.newFixedThreadPool(5);

        for (int i = 0; i < 1000; ++i) {
            BoundedExecutor bounded = new BoundedExecutor(10, underlying);
            AtomicInteger completed = new AtomicInteger(0);
            for (int j = 0; j < 10; ++j) {
                bounded.execute(completed::incrementAndGet);
            }
            bounded.drain();
            assertEquals(10, completed.get());
        }

        underlying.shutdown();
        underlying.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Test(timeout = 5000, expected = RejectedExecutionException.class)
    public void rejectTasksAfterDrain() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        BoundedExecutor bounded = new BoundedExecutor(10, underlying);

        bounded.execute(() -> {
        });

        bounded.drain();
        try {
            bounded.execute(() -> {
            });
        } finally {
            underlying.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void callDrainMultipleTime() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        BoundedExecutor bounded = new BoundedExecutor(10, underlying);

        bounded.execute(() -> {
        });

        bounded.drain();
        bounded.drain();

        underlying.shutdownNow();
    }
}
