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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class BoundedExecutor implements Executor {

    private final int maxTasks;

    private final Semaphore semaphore;

    private final KeySequentialExecutor executor;

    private boolean drained = false;

    public BoundedExecutor(int maxTasks, KeySequentialExecutor executor) {
        this.maxTasks = maxTasks;
        this.semaphore = new Semaphore(maxTasks);
        this.executor = executor;
    }

    public BoundedExecutor(int maxTasks, Executor underlyingExecutor) {
        this(maxTasks, new KeySequentialExecutor(underlyingExecutor));
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) throw new NullPointerException("Task is null");
        synchronized (this) {
            if (drained) {
                throw new RejectedExecutionException(getClass().getSimpleName() + " drained");
            } else {
                semaphore.acquireUninterruptibly();
            }
        }
        try {
            executor.execute(new KeyRunnable<>(
                    task,
                    () -> {
                        try {
                            task.run();
                        } finally {
                            semaphore.release();
                        }
                    })
            );
        } catch (RejectedExecutionException e) {
            semaphore.release();
            throw e;
        }
    }

    public synchronized boolean drain(long timeout, TimeUnit unit) throws InterruptedException {
        if (!drained && semaphore.tryAcquire(maxTasks, timeout, unit)) {
            drained = true;
        }
        return drained;
    }
}
