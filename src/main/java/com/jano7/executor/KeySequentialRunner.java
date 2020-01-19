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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class KeySequentialRunner<Key> {

    private final class KeyRunner {

        private final LinkedList<Runnable> tasks = new LinkedList<>();
        private boolean active = false;

        public synchronized void run(Runnable task) {
            if (active) {
                tasks.addFirst(task);
            } else {
                active = true;
                runTask(task);
            }
        }

        private void runTask(Runnable task) {
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

        private synchronized Runnable nextTask() {
            Runnable runnable = tasks.pollLast();
            if (runnable == null) {
                active = false;
            }
            return runnable;
        }

        public synchronized boolean isActive() {
            return active;
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

    public synchronized void run(Key key, Runnable task) {
        KeyRunner runner = keyRunners.get(key);
        if (runner == null) {
            runner = new KeyRunner();
            keyRunners.put(key, runner);
        }
        runner.run(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                exceptionHandler.handleTaskException(t);
            }
        });
        scavengeInactiveRunners();
    }

    private void scavengeInactiveRunners() {
        for (Key key : new ArrayList<>(keyRunners.keySet())) {
            if (!keyRunners.get(key).isActive()) {
                keyRunners.remove(key);
            }
        }
    }
}
