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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.jano7.executor.Util.checkNotNull;

public final class KeySequentialRunner<Key> {

    private final class KeyRunner {

        private boolean notTriggered = true;
        private final TaskQueue tasks = new TaskQueue();
        private final Key key;

        KeyRunner(Key key) {
            this.key = key;
        }

        void enqueue(Runnable task) {
            if (!tasks.enqueue(task)) {
                System.out.println("hello"); // TODO remove
                throw new RejectedExecutionException(rejection());
            }
        }

        synchronized void triggerRun() {
            if (notTriggered) {
                try {
                    run(tasks.dequeue());
                    notTriggered = false;
                } catch (RejectedExecutionException e) {
                    synchronized (keyRunners) {
                        if (tasks.isEmpty()) {
                            keyRunners.remove(key);
                        }
                    }
                    throw new RejectedExecutionException(rejection(), e);
                }
            }
        }

        private void run(Runnable task) {
            underlyingExecutor.execute(() -> {
                runSafely(task);
                Runnable next = tasks.dequeue();
                if (next == null) {
                    synchronized (keyRunners) {
                        next = tasks.dequeue();
                        if (next == null) {
                            keyRunners.remove(key);
                        }
                    }
                }
                if (next != null) {
                    try {
                        run(next);
                    } catch (RejectedExecutionException e) {
                        // complete the task and the remaining ones on this thread when the execution is rejected
                        tasks.rejectNew();
                        do {
                            runSafely(next);
                        } while ((next = tasks.dequeue()) != null);
                        synchronized (keyRunners) {
                            keyRunners.remove(key);
                        }
                    }
                }
            });
        }

        private void runSafely(Runnable task) {
            try {
                task.run();
            } catch (Throwable t) {
                exceptionHandler.onException(key, t);
            }
        }

        private String rejection() {
            return "task for the key '" + key + "' rejected";
        }
    }

    private final Executor underlyingExecutor;
    private final TaskExceptionHandler<Key> exceptionHandler;
    private final HashMap<Key, KeyRunner> keyRunners = new HashMap<>();

    public KeySequentialRunner(Executor underlyingExecutor) {
        this.underlyingExecutor = underlyingExecutor;
        this.exceptionHandler = new TaskExceptionHandler<Key>() {
        };
    }

    public KeySequentialRunner(Executor underlyingExecutor, TaskExceptionHandler<Key> exceptionHandler) {
        this.underlyingExecutor = underlyingExecutor;
        this.exceptionHandler = exceptionHandler;
    }

    public void run(Key key, Runnable task) {
        checkNotNull(task);
        KeyRunner runner;
        synchronized (keyRunners) {
            runner = keyRunners.get(key);
            if (runner == null) {
                runner = new KeyRunner(key);
                keyRunners.put(key, runner);
            }
            runner.enqueue(task);
        }
        runner.triggerRun();
    }
}
