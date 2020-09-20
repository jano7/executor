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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jano7.executor.BoundedStrategy.BLOCK;
import static com.jano7.executor.BoundedStrategy.REJECT;
import static com.jano7.executor.TestUtils.doSomething;
import static org.junit.Assert.*;

public class KeySequentialBoundedExecutorTest {

    @Test(timeout = 5000)
    public void blockWhenLimitReached() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        AtomicInteger completed = new AtomicInteger(0);
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        Runnable blockingTask = new KeyRunnable<>("key", () -> {
            try {
                block.await();
            } catch (InterruptedException ignored) {
            }
            completed.incrementAndGet();
        });
        Runnable simpleTask = new KeyRunnable<>("key", completed::incrementAndGet);

        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(5, BLOCK, underlyingExecutor);
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

    @Test(timeout = 5000, expected = RejectedExecutionException.class)
    public void rejectWhenLimitReached() {
        ExecutorService underlyingExecutor = Executors.newSingleThreadExecutor();
        CountDownLatch block = new CountDownLatch(1);
        Runnable blockingTask = () -> {
            try {
                block.await();
            } catch (InterruptedException ignored) {
            }
        };

        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(2, REJECT, underlyingExecutor);
        bounded.execute(blockingTask);
        bounded.execute(blockingTask);

        try {
            bounded.execute(blockingTask);
        } finally {
            underlyingExecutor.shutdownNow();
        }
    }

    @Test(timeout = 10000)
    public void drain() throws InterruptedException {
        for (int i = 0; i < 1000; ++i) {
            ExecutorService underlying = Executors.newFixedThreadPool(5);
            KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(20, BLOCK, underlying);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger completed = new AtomicInteger(0);

            for (int j = 0; j < 10; ++j) {
                if (j == 5) {
                    bounded.execute(() -> {
                        try {
                            latch.await();
                            completed.incrementAndGet();
                        } catch (InterruptedException ignored) {
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
        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(10, BLOCK, underlying);

        bounded.execute(doSomething);

        bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS);
        try {
            bounded.execute(doSomething);
        } finally {
            underlying.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void safeToCallDrainMultipleTime() throws InterruptedException {
        ExecutorService underlying = Executors.newCachedThreadPool();
        KeySequentialBoundedExecutor bounded = new KeySequentialBoundedExecutor(10, BLOCK, underlying);

        bounded.execute(doSomething);

        assertTrue(bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS));
        assertTrue(bounded.drain(Long.MAX_VALUE, TimeUnit.SECONDS));

        underlying.shutdownNow();
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

        Executor bounded = new KeySequentialBoundedExecutor(1, BLOCK, underlying);

        boolean thrown = false;
        try {
            bounded.execute(doSomething);
        } catch (RejectedExecutionException e) {
            thrown = true;
        }
        bounded.execute(doSomething);

        assertTrue(thrown);
    }

    @Test(expected = NullPointerException.class)
    public void throwExceptionWhenTaskIsNull() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        Executor bounded = new KeySequentialBoundedExecutor(10, BLOCK, underlying);

        try {
            bounded.execute(null);
        } finally {
            underlying.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void underLoad() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialBoundedExecutor boundedExecutor = new KeySequentialBoundedExecutor(5, BLOCK, underlyingExecutor);
        List<Integer> processed = Collections.synchronizedList(new LinkedList<>());

        for (int i = 0; i < 1000; ++i) {
            final int toProcess = i;
            boundedExecutor.execute(new KeyRunnable<>(i % 2, () -> processed.add(toProcess)));
        }

        boundedExecutor.drain(Long.MAX_VALUE, TimeUnit.SECONDS);
        underlyingExecutor.shutdownNow();

        int previousOdd = -1;
        int previousEven = -2;
        for (int p : processed) {
            if (p % 2 == 0) {
                assertEquals(previousEven + 2, p);
                previousEven = p;
            } else {
                assertEquals(previousOdd + 2, p);
                previousOdd = p;
            }
        }
    }
}
