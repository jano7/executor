package com.jano7.executor;

import java.util.LinkedList;

class TaskQueue {

    private boolean accept = true;
    private final LinkedList<Runnable> tasks = new LinkedList<>();

    synchronized boolean enqueue(Runnable task) {
        if (accept) {
            return tasks.offer(task);
        }
        return false;
    }

    synchronized Runnable dequeue() {
        return tasks.poll();
    }

    synchronized boolean isEmpty() {
        return tasks.isEmpty();
    }

    synchronized void rejectNew() {
        accept = false;
    }
}
