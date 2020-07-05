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

import java.util.LinkedList;
import java.util.List;

import static com.jano7.executor.TestUtil.doSomething;
import static org.junit.Assert.*;

public class TaskQueueTest {

    @Test(timeout = 5000)
    public void enqueueDequeue() throws InterruptedException {
        TaskQueue taskQueue = new TaskQueue();
        Thread enqueueThread = new Thread(() -> {
            for (int i = 0; i < 100; ++i) {
                taskQueue.enqueue(new KeyRunnable<>(i, doSomething));
            }
        });
        enqueueThread.start();
        for (int i = 0; i < 100; ) {
            Runnable task = taskQueue.dequeue();
            if (task == null) {
                Thread.sleep(100);
            } else {
                assertEquals(task, new KeyRunnable<>(i, doSomething));
                ++i;
            }
        }
    }

    private volatile List<Runnable> dequeued = null;

    @Test(timeout = 5000)
    public void reject() throws InterruptedException {
        TaskQueue taskQueue = new TaskQueue();
        Thread rejectTrigger = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            dequeued = taskQueue.rejectNew();
        });
        rejectTrigger.start();

        List<Runnable> enqueued = new LinkedList<>();
        while (true) {
            Runnable task = new KeyRunnable<>(Math.random(), doSomething);
            if (!taskQueue.enqueue(task)) {
                assertTrue(true);
                break;
            }
            enqueued.add(task);
            Thread.sleep(100);
        }
        rejectTrigger.join();

        assertArrayEquals(enqueued.toArray(), dequeued.toArray());
        assertNull(taskQueue.dequeue());
    }
}
