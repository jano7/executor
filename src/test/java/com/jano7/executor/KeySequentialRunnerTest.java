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

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeySequentialRunnerTest {

    private static final int THREAD_COUNT = 10;
    private final ExecutorService underlyingExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
    private final KeySequentialRunner<String> runner = new KeySequentialRunner<>(underlyingExecutor);
    private final Map<String, Long> threadIdMap = Collections.synchronizedMap(new HashMap<>());

    @After
    public void shutDown() throws InterruptedException {
        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Test(timeout = 5000)
    public void noMemoryLeak() throws
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InterruptedException {
        Field keyRunners = KeySequentialRunner.class.getDeclaredField("keyRunners");
        keyRunners.setAccessible(true);
        synchronized (runner) {
            assertTrue(((Map<?, ?>) keyRunners.get(runner)).isEmpty());
        }

        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT / 2);
        for (int i = THREAD_COUNT / 2; i > 0; --i) {
            runner.run(Integer.toString(i), () -> run(latch, threadIdMap, "t"));
        }

        latch.await();

        synchronized (runner) {
            assertEquals(THREAD_COUNT / 2, ((Map<?, ?>) keyRunners.get(runner)).size());
        }

        Thread.sleep(1000);

        runner.run("key", () -> run(latch, threadIdMap, "t"));

        synchronized (runner) {
            assertEquals(1, ((Map<?, ?>) keyRunners.get(runner)).size());
        }
    }

    private static void run(CountDownLatch latch, Map<String, Long> threadIdMap, String threadId) {
        threadIdMap.put(threadId, Thread.currentThread().getId());
        latch.countDown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
