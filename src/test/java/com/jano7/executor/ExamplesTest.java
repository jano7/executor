package com.jano7.executor;

import org.junit.Test;

import static com.jano7.executor.Examples.basicExample;
import static com.jano7.executor.Examples.boundedExecutorExample;

public class ExamplesTest {

    @Test
    public void examples() throws InterruptedException {
        basicExample();
        boundedExecutorExample();
    }
}
