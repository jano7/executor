/*
MIT License

Copyright (c) 2019 Jan Gaspar

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

import static org.junit.Assert.assertEquals;

public class KeySequentialExecutorTest {

    @Test(timeout = 5000)
    public void executeTasksInCorrectOrder() throws InterruptedException {
        List<Integer> processed = Collections.synchronizedList(new LinkedList<>());
        CountDownLatch key1Latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        Runnable key1Task1 = () -> {
            try {
                key1Latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            processed.add(1);
        };
        Runnable key1Task2 = () -> {
            processed.add(2);
            doneLatch.countDown();
        };
        Runnable key2Task1 = () -> processed.add(3);
        Runnable key2Task2 = () -> {
            processed.add(4);
            key1Latch.countDown();
        };

        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
        executor.execute(new KeyRunnable<>("key1", key1Task1));
        executor.execute(new KeyRunnable<>("key1", key1Task2));
        executor.execute(new KeyRunnable<>("key2", key2Task1));
        executor.execute(new KeyRunnable<>("key2", key2Task2));

        doneLatch.await();

        assertEquals(3, processed.get(0).intValue());
        assertEquals(4, processed.get(1).intValue());
        assertEquals(1, processed.get(2).intValue());
        assertEquals(2, processed.get(3).intValue());

        underlyingExecutor.shutdownNow();
    }

    @Test(timeout = 5000)
    public void underLoad() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
        List<Integer> processed = Collections.synchronizedList(new LinkedList<>());

        for (int i = 0; i < 1000; ++i) {
            final int toProcess = i;
            executor.execute(new KeyRunnable<>(i % 2, () -> processed.add(toProcess)));
        }

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

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

    @Test(timeout = 5000)
    public void exceptionHandling() throws InterruptedException {
        LinkedBlockingQueue<Throwable> handledExceptions = new LinkedBlockingQueue<>();
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor executor = new KeySequentialExecutor(
                underlyingExecutor,
                new TaskExceptionHandler() {
                    @Override
                    public void handleTaskException(Throwable t) {
                        handledExceptions.offer(t);
                    }
                }
        );
        RuntimeException exception1 = new RuntimeException("test1");
        RuntimeException exception2 = new RuntimeException("test2");

        executor.execute(
                new KeyRunnable<>("key", () -> {
                    throw exception1;
                })
        );
        executor.execute(
                new KeyRunnable<>("key", () -> {
                    throw exception2;
                })
        );

        assertEquals(exception1, handledExceptions.take());
        assertEquals(exception2, handledExceptions.take());

        underlyingExecutor.shutdownNow();
    }

    @Test(timeout = 5000)
    public void taskExecutionDuringShutdown() throws InterruptedException {
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);

        Runnable key1Task1 = () -> {
            try {
                latch1.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            queue.offer(1);
        };
        Runnable key1Task2 = () -> {
            queue.offer(2);
            latch2.countDown();
        };
        Runnable key2Task1 = () -> {
            try {
                latch2.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            queue.offer(3);
        };
        Runnable key2Task2 = () -> queue.offer(4);

        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor executor = new KeySequentialExecutor(underlyingExecutor);
        executor.execute(new KeyRunnable<>("key2", key2Task1));
        executor.execute(new KeyRunnable<>("key2", key2Task2));
        executor.execute(new KeyRunnable<>("key1", key1Task1));
        executor.execute(new KeyRunnable<>("key1", key1Task2));

        latch1.countDown();

        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        assertEquals(1, queue.take().intValue());
        assertEquals(2, queue.take().intValue());
        assertEquals(3, queue.take().intValue());
        assertEquals(4, queue.take().intValue());
    }

    @Test(timeout = 5000, expected = NullPointerException.class)
    public void throwExceptionWhenTaskIsNull() {
        ExecutorService underlying = Executors.newCachedThreadPool();
        Executor executor = new KeySequentialExecutor(underlying);

        try {
            executor.execute(null);
        } finally {
            underlying.shutdownNow();
        }
    }
}
