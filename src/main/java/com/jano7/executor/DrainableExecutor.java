package com.jano7.executor;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public interface DrainableExecutor extends Executor {

    boolean drain(long timeout, TimeUnit unit) throws InterruptedException;
}
