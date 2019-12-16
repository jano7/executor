package com.jano7.executor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class KeyRunnableTest {

    private final Runnable r1 = new KeyRunnable<>("1", System::getenv);
    private final Runnable r2 = new KeyRunnable<>("1", System::currentTimeMillis);
    private final Runnable r3 = new KeyRunnable<>("2", System::currentTimeMillis);

    @Test
    public void keyRunnableHashCode() {
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r2.hashCode(), r3.hashCode());
        assertEquals(r1.hashCode(), "1".hashCode());
    }

    @Test
    public void keyRunnableEquals() {
        assertEquals(r1, r2);
        assertNotEquals(r2, r3);
    }
}
