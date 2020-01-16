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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeySequentialRunnerTest {

    private static final int THREAD_COUNT = 10;

    @Test(timeout = 5000)
    public void noMemoryLeak() throws
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
        Executor testExecutor = (Runnable runnable) -> {
            try {
                underlyingExecutor.execute(runnable);
            } catch (RejectedExecutionException ignored) {
                runnable.run();
            }
        };

        KeySequentialRunner<String> runner = new KeySequentialRunner<>(testExecutor);

        Field keyRunners = KeySequentialRunner.class.getDeclaredField("keyRunners");
        keyRunners.setAccessible(true);

        assertTrue(((Map<?, ?>) keyRunners.get(runner)).isEmpty());

        final CountDownLatch latch = new CountDownLatch(1);
        for (int i = THREAD_COUNT; i > 0; --i) {
            runner.run(Integer.toString(i), () -> await(latch));
        }

        assertEquals(THREAD_COUNT, ((Map<?, ?>) keyRunners.get(runner)).size());

        latch.countDown();
        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        runner.run("key", () -> {
        });

        assertEquals(0, ((Map<?, ?>) keyRunners.get(runner)).size());
    }

    @Test(timeout = 5000)
    public void highLoad() throws InterruptedException {
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
        KeySequentialRunner<Integer> runner = new KeySequentialRunner<>(underlyingExecutor);
        List<Integer> processed = Collections.synchronizedList(new LinkedList<>());

        for (int i = 0; i < 1000; ++i) {
            final int toProcess = i;
            runner.run(i % 2, () -> processed.add(toProcess));
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
