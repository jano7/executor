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
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class KeySequentialRunner<Key> {

    private final class KeyRunner {

        private final LinkedList<Runnable> tasks = new LinkedList<>();
        private final Key key;

        KeyRunner(Key key) {
            this.key = key;
        }

        synchronized void addTask(Runnable task) {
            tasks.add(task);
        }

        private synchronized Runnable pollTask() {
            return tasks.poll();
        }

        void runTask(Runnable task) {
            underlyingExecutor.execute(() -> {
                task.run();
                Runnable next = nextTask();
                if (next != null) {
                    try {
                        runTask(next);
                    } catch (RejectedExecutionException executorStopping) {
                        do {
                            next.run();
                        } while ((next = nextTask()) != null);
                    }
                }
            });
        }

        private Runnable nextTask() {
            Runnable runnable = pollTask();
            if (runnable == null) {
                synchronized (KeySequentialRunner.this) {
                    runnable = pollTask();
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
        Runnable safeTask = () -> {
            try {
                task.run();
            } catch (Throwable t) {
                exceptionHandler.handleTaskException(t);
            }
        };
        boolean first = false;
        KeyRunner runner;
        synchronized (this) {
            runner = keyRunners.get(key);
            if (runner == null) {
                runner = new KeyRunner(key);
                keyRunners.put(key, runner);
                first = true;
            } else {
                runner.addTask(safeTask);
            }
        }
        if (first) {
            runner.runTask(safeTask);
        }
    }
}
