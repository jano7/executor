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

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.jano7.executor.Util.checkNotNull;

public final class KeySequentialRunner<Key> {

    private final class KeyRunner {

        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private final Key key;

        KeyRunner(Key key) {
            this.key = key;
        }

        void queueTask(Runnable task) {
            tasks.offer(task);
        }

        void runTask(Runnable task) {
            underlyingExecutor.execute(() -> {
                runSafely(task);
                Runnable next = nextTask();
                if (next != null) {
                    try {
                        runTask(next);
                    } catch (RejectedExecutionException e) {
                        // complete the remaining tasks on this thread
                        do {
                            runSafely(next);
                        } while ((next = nextTask()) != null);
                    }
                }
            });
        }

        private void runSafely(Runnable task) {
            try {
                task.run();
            } catch (Throwable t) {
                exceptionHandler.handleTaskException(t);
            }
        }

        private Runnable nextTask() {
            Runnable runnable = tasks.poll();
            if (runnable == null) {
                synchronized (keyRunners) {
                    runnable = tasks.poll();
                    if (runnable == null) {
                        keyRunners.remove(key);
                    }
                }
            }
            return runnable;
        }
    }

    private final Executor underlyingExecutor;
    private final TaskExceptionHandler exceptionHandler;
    private final HashMap<Key, KeyRunner> keyRunners = new HashMap<>();

    public KeySequentialRunner(Executor underlyingExecutor) {
        this.underlyingExecutor = underlyingExecutor;
        this.exceptionHandler = new TaskExceptionHandler() {
        };
    }

    public KeySequentialRunner(Executor underlyingExecutor, TaskExceptionHandler exceptionHandler) {
        this.underlyingExecutor = underlyingExecutor;
        this.exceptionHandler = exceptionHandler;
    }

    public void run(Key key, Runnable task) {
        checkNotNull(task);
        synchronized (this) {
            KeyRunner newRunner = null;
            synchronized (keyRunners) {
                KeyRunner runner = keyRunners.get(key);
                if (runner == null) {
                    newRunner = new KeyRunner(key);
                    keyRunners.put(key, newRunner);
                } else {
                    runner.queueTask(task);
                }
            }
            if (newRunner != null) {
                try {
                    newRunner.runTask(task);
                } catch (RejectedExecutionException e) {
                    synchronized (keyRunners) { // TODO test
                        keyRunners.remove(key);
                    }
                    throw e;
                }
            }
        }
    }
}
