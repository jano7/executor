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
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KeySequentialRunnerTest {


    @Test(timeout = 5000)
    public void noMemoryLeak() throws InterruptedException, IllegalAccessException, NoSuchFieldException {
        final int threadCount = 10;
        ExecutorService underlyingExecutor = Executors.newFixedThreadPool(threadCount);
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
        for (int i = threadCount; i > 0; --i) {
            runner.run(Integer.toString(i), () -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertEquals(threadCount, ((Map<?, ?>) keyRunners.get(runner)).size());

        latch.countDown();
        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);

        runner.run("key", () -> {
        });

        assertEquals(0, ((Map<?, ?>) keyRunners.get(runner)).size());
    }
}
