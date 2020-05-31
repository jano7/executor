package com.jano7.executor;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jano7.executor.BoundedStrategy.BLOCK;
import static com.jano7.executor.BoundedStrategy.REJECT;
import static org.junit.Assert.*;

public class BoundedExecutorTest {

    @Test(timeout = 5000)
    public void blockWhenLimitReached() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(10);
        KeySequentialExecutor keySequentialExecutor = new KeySequentialExecutor(underlyingExecutor);
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

        BoundedExecutor bounded = new BoundedExecutor(5, BLOCK, keySequentialExecutor);
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
        CountDownLatch done = new CountDownLatch(1);
        Runnable blockingTask = () -> {
            try {
                block.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        BoundedExecutor bounded = new BoundedExecutor(2, REJECT, underlyingExecutor);
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
            BoundedExecutor bounded = new BoundedExecutor(20, BLOCK, underlying);
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
        BoundedExecutor bounded = new BoundedExecutor(10, BLOCK, underlying);

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
        BoundedExecutor bounded = new BoundedExecutor(10, BLOCK, underlying);

        bounded.execute(() -> {
        });

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

        Executor bounded = new BoundedExecutor(1, BLOCK, underlying);

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
