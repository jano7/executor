package com.jano7.executor;

class Util {

    static void checkNotNull(Runnable task) {
        if (task == null) {
            throw new NullPointerException("Task is null");
        }
    }
}
