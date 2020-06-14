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

import static com.jano7.executor.Util.checkNotNull;

public final class KeySequentialRunner<Key> {

    private final class KeyRunner {

        private boolean trigger = true;
        private boolean acceptTask = true;
        private final LinkedList<Runnable> tasks = new LinkedList<>();
        private final Key key;

        KeyRunner(Key key) {
            this.key = key;
        }

        void enqueue(Runnable task) {
            synchronized (tasks) {
                if (acceptTask) {
                    tasks.offer(task);
                }
            }
        }

        private Runnable dequeue() {
            synchronized (tasks) {
                return tasks.poll();
            }
        }

        synchronized void triggerExecution() {
            if (trigger) {
                try {
                    executeNextTask();
                    trigger = false;
                } catch (RejectedExecutionException e) {
                    throw new RejectedExecutionException("task for the key '" + key + "' rejected", e);
                }
            }
        }

        private void executeNextTask() {
            underlyingExecutor.execute(() -> {
                Runnable task = nextTask();
                if (task != null) {
                    runSafely(task);
                    try {
                        executeNextTask();
                    } catch (RejectedExecutionException e) {
                        // complete the task and the remaining ones on this thread when the execution is rejected
                        synchronized (tasks) {
                            acceptTask = false;
                        }
                        do {
                            runSafely(task);
                        } while ((task = nextTask()) != null);
                    }
                }
            });
        }

        private void runSafely(Runnable task) {
            try {
                task.run();
            } catch (Throwable t) {
                exceptionHandler.handleTaskException(key, t);
            }
        }

        private Runnable nextTask() {
            Runnable runnable = dequeue();
            if (runnable == null) {
                synchronized (keyRunners) {
                    runnable = dequeue();
                    if (runnable == null) {
                        keyRunners.remove(key);
                    }
                }
            }
            return runnable;
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
        runner.triggerExecution();
    }
}
