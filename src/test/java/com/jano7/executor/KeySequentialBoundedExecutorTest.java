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

public class KeySequentialBoundedExecutorTest {

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

        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(5, executor);
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
        Executor bounded = new KeySequentialBoundedExecutor(10, executor);

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
        Executor bounded = new KeySequentialBoundedExecutor(1, executor);

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

    @Test(timeout = 10000)
    public void drain() throws InterruptedException {
        for (int i = 0; i < 1000; ++i) {
            ExecutorService underlying = Executors.newFixedThreadPool(5);
            KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(20, underlying);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger(0);

            for (int j = 0; j < 10; ++j) {
                if (j == 5) {
                    bounded.execute(() -> {
                        try {
                            latch.await();
                            completed.incrementAndGet();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    bounded.execute(completed::incrementAndGet);
                }
            }

            assertFalse(bounded.drain(1, TimeUnit.MILLISECONDS));

            latch.countDown();

            assertTrue(bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS));
            assertEquals(10, completed.get());
            assertTrue(underlying.shutdownNow().isEmpty());
        }
    }

    @Test(timeout = 5000, expected = RejectedExecutionException.class)
    public void rejectTasksAfterDrain() throws InterruptedException {
        ExecutorService underlying = Executors.newCachedThreadPool();
        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(10, underlying);

        bounded.execute(() -> {
        });

        bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS);
        try {
            bounded.execute(() -> {
            });
        } finally {
            underlying.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void safeToCallDrainMultipleTime() throws InterruptedException {
        ExecutorService underlying = Executors.newCachedThreadPool();
        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(10, underlying);

        bounded.execute(() -> {
        });

        assertTrue(bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS));
        assertTrue(bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS));

        underlying.shutdownNow();
    }
}
