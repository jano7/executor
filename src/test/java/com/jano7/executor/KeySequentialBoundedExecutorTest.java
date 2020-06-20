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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.jano7.executor.BoundedStrategy.BLOCK;
import static org.junit.Assert.assertEquals;

public class KeySequentialBoundedExecutorTest {

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
